package com.gpstl.joyn.auth.service;

import com.gpstl.joyn.common.model.User;
import com.gpstl.joyn.config.SecurityConfig;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Fabrique les tokens JWT (le "badge" donné à l'utilisateur après sa connexion).
 *
 * Le token respecte exactement le contrat déjà vérifié par SecurityConfig :
 *  - signé en HS256 avec la même clé joyn.security.jwt.secret ;
 *  - "sub"  = id de l'utilisateur (users.id) ;
 *  - "role" = CANDIDATE, RECRUITER ou ADMIN ;
 *  - "exp"  = date d'expiration (après, le token est refusé avec un 401).
 */
@Service
public class JwtTokenService {

    private final JwtEncoder encoder;
    private final long expirationSeconds;

    public JwtTokenService(@Value("${joyn.security.jwt.secret}") String secret,
                           @Value("${joyn.security.jwt.expiration-seconds:3600}") long expirationSeconds) {
        // Même clé que le décodeur de SecurityConfig : un token signé ici sera accepté là-bas.
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(SecurityConfig.secretKey(secret)));
        this.expirationSeconds = expirationSeconds;
    }

    /** Crée un token signé pour cet utilisateur. */
    public String createToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(expirationSeconds))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** Durée de validité d'un token, en secondes (renvoyée au front dans expires_in). */
    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
