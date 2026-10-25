package com.gpstl.joyn.auth.exception;

/**
 * Email inconnu OU mot de passe faux. Renvoyée au client en 401.
 * Volontairement le même message dans les deux cas : on ne dit jamais à un attaquant
 * si un email existe ou non dans la base.
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Email ou mot de passe incorrect");
    }
}
