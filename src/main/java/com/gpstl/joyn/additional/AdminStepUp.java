package com.gpstl.joyn.additional;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AdminStepUp {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final Map<String, String> secrets;
    // Réservé aux environnements de démonstration : désactivé par défaut.
    private final boolean testCodeEnabled;
    private final String testCode;

    public AdminStepUp(JdbcTemplate jdbc, PasswordEncoder passwords,
            @Value("${JOYN_ADMIN_TOTP_SECRETS:}") String configuredSecrets,
            @Value("${JOYN_ADMIN_ALLOW_TEST_CODE:false}") boolean allowTestCode,
            @Value("${JOYN_ADMIN_TEST_CODE:123456}") String configuredTestCode) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        if (!configuredTestCode.matches("[0-9]{6}")) {
            throw new IllegalStateException("Invalid six-digit administrative test code configuration");
        }
        this.testCodeEnabled = allowTestCode && configuredSecrets.isBlank();
        this.testCode = configuredTestCode;
        try {
            this.secrets = configuredSecrets.isBlank() ? Map.of() : new JsonMapper().readValue(configuredSecrets, new TypeReference<Map<String, String>>() {});
        } catch (RuntimeException e) {
            throw new IllegalStateException("Invalid administrative verification configuration");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = org.springframework.web.server.ResponseStatusException.class)
    public void verify(long user, String password, String code) {
        var accounts = jdbc.queryForList("""
                SELECT email, password_hash FROM users WHERE id=? AND role='ADMIN' AND status='ACTIVE'
                """, user);
        if (accounts.isEmpty()) throw ActorAccess.error(403, "Accès réservé à l’administration");
        var account = accounts.getFirst();
        String secret = secrets.get(((String) account.get("email")).toLowerCase(Locale.ROOT));
        boolean useTestCode = secret == null && testCodeEnabled;
        if (secret == null && !useTestCode) {
            throw ActorAccess.error(412, "Second facteur de ce compte non configuré");
        }

        int attempts = jdbc.queryForObject("""
                INSERT INTO admin_moderation_step_up(user_id, attempts) VALUES (?, 1)
                ON CONFLICT (user_id) DO UPDATE SET
                    attempts = CASE WHEN admin_moderation_step_up.window_started_at <= now() - interval '1 minute'
                         THEN 1 ELSE LEAST(admin_moderation_step_up.attempts + 1, 6) END,
                    window_started_at = CASE WHEN admin_moderation_step_up.window_started_at <= now() - interval '1 minute'
                         THEN now() ELSE admin_moderation_step_up.window_started_at END
                RETURNING attempts
                """, Integer.class, user);
        if (attempts > 5) throw ActorAccess.error(429, "Réessayez dans une minute");
        if (password.getBytes(StandardCharsets.UTF_8).length > 72 ||
                !passwords.matches(password, (String) account.get("password_hash"))) {
            throw ActorAccess.error(403, "Mot de passe ou code invalide");
        }

        // Mode de test explicitement activé uniquement si aucun secret TOTP n'est configuré.
        // Il conserve la vérification du mot de passe et la limitation de tentatives,
        // mais NE constitue PAS un second facteur sécurisé : ne pas utiliser en production.
        if (useTestCode) {
            if (MessageDigest.isEqual(testCode.getBytes(StandardCharsets.US_ASCII),
                    code.getBytes(StandardCharsets.US_ASCII))) {
                return;
            }
            throw ActorAccess.error(403, "Mot de passe ou code invalide");
        }

        long step = Instant.now().getEpochSecond() / 30;
        byte[] key;
        try {
            key = base32(secret);
        } catch (IllegalArgumentException e) {
            throw ActorAccess.error(412, "Second facteur de ce compte non configuré");
        }
        for (long counter = step - 1; counter <= step; counter++) {
            if (MessageDigest.isEqual(totp(key, counter), code.getBytes(StandardCharsets.US_ASCII))
                    && jdbc.update("""
                        UPDATE admin_moderation_step_up SET last_totp_step=?
                        WHERE user_id=? AND last_totp_step < ?
                        """, counter, user, counter) == 1) {
                return;
            }
        }
        throw ActorAccess.error(403, "Mot de passe ou code invalide");
    }

    private static byte[] totp(byte[] secret, long counter) {
        try {
            var mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset = hash[hash.length - 1] & 15;
            int binary = ByteBuffer.wrap(hash, offset, 4).getInt() & Integer.MAX_VALUE;
            return String.format(Locale.ROOT, "%06d", binary % 1_000_000).getBytes(StandardCharsets.US_ASCII);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("TOTP verification unavailable", e);
        }
    }

    private static byte[] base32(String value) {
        String normalized = value.strip().toUpperCase(Locale.ROOT).replaceFirst("=+$", "");
        if (normalized.length() < 32 || normalized.length() > 128) throw new IllegalArgumentException();
        var bytes = new java.io.ByteArrayOutputStream();
        int pending = 0;
        int bits = 0;
        for (char c : normalized.toCharArray()) {
            int digit = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(c);
            if (digit < 0) throw new IllegalArgumentException();
            pending = (pending << 5) | digit;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                bytes.write((pending >> bits) & 255);
            }
        }
        return bytes.toByteArray();
    }
}
