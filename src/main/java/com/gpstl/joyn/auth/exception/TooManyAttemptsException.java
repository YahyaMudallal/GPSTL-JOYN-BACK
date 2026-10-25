package com.gpstl.joyn.auth.exception;

/** Trop d'essais ratés pour cet email : connexion bloquée un moment (protection brute-force). Renvoyée en 429. */
public class TooManyAttemptsException extends RuntimeException {

    /** Nombre de secondes avant de pouvoir réessayer (renvoyé dans l'en-tête HTTP Retry-After). */
    private final long retryAfterSeconds;

    public TooManyAttemptsException(long retryAfterSeconds) {
        super("Trop de tentatives de connexion. Réessayez dans quelques minutes.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
