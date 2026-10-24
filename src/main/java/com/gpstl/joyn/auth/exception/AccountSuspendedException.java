package com.gpstl.joyn.auth.exception;

/** Identifiants corrects mais compte suspendu par un administrateur (users.status = SUSPENDED). Renvoyée en 403. */
public class AccountSuspendedException extends RuntimeException {
    public AccountSuspendedException() {
        super("Ce compte est suspendu");
    }
}
