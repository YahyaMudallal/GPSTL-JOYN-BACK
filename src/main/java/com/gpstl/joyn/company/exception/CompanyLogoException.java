package com.gpstl.joyn.company.exception;

/**
 * Erreur de la gestion des logos d'entreprise (R84), avec son code HTTP.
 * Même principe que UserProfilePictureException (U86).
 */
public class CompanyLogoException extends RuntimeException {

    private final int status;

    public CompanyLogoException(int status, String message) {
        super(message);
        this.status = status;
    }

    public CompanyLogoException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
