package com.gpstl.joyn.auth.service;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protection contre le brute-force (un robot qui essaie des milliers de mots de passe).
 *
 * Règle : après 5 échecs pour un même email, la connexion de cet email est bloquée 15 minutes.
 * Une connexion réussie remet le compteur à zéro.
 *
 * Version simple : les compteurs sont gardés en mémoire. Ils sont donc perdus au redémarrage
 * et ne sont pas partagés si l'API tourne sur plusieurs serveurs. Suffisant pour le projet ;
 * plus tard, on pourra les stocker dans Redis ou en base.
 */
@Service
public class LoginAttemptService {

    static final int MAX_FAILURES = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    /** État d'un email : nombre d'échecs et, s'il est bloqué, jusqu'à quand. */
    private record Attempts(int failures, Instant lockedUntil) {
    }

    private final Map<String, Attempts> attemptsByEmail = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptService() {
        this(Clock.systemUTC());
    }

    /** Constructeur utilisé par les tests pour simuler le passage du temps. */
    LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    /** Nombre de secondes de blocage restantes pour cet email (0 s'il n'est pas bloqué). */
    public long secondsUntilUnlocked(String email) {
        Attempts attempts = attemptsByEmail.get(email);
        if (attempts == null || attempts.lockedUntil() == null) {
            return 0;
        }
        long remaining = Duration.between(clock.instant(), attempts.lockedUntil()).toSeconds();
        if (remaining <= 0) {
            attemptsByEmail.remove(email); // blocage terminé : on repart de zéro
            return 0;
        }
        return remaining;
    }

    /** À appeler après un échec : au 5e échec, l'email est bloqué. */
    public void recordFailure(String email) {
        attemptsByEmail.compute(email, (key, current) -> {
            int failures = (current == null ? 0 : current.failures()) + 1;
            Instant lockedUntil = failures >= MAX_FAILURES ? clock.instant().plus(LOCK_DURATION) : null;
            return new Attempts(failures, lockedUntil);
        });
    }

    /** À appeler après une connexion réussie : on oublie les échecs précédents. */
    public void recordSuccess(String email) {
        attemptsByEmail.remove(email);
    }
}
