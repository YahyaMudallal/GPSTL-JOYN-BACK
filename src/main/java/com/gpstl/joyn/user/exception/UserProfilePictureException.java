package com.gpstl.joyn.user.exception;

/**
 * Exception levée lors des opérations de gestion des photos de profil utilisateur.
 */
public class UserProfilePictureException extends RuntimeException {

    private final int status;

    public UserProfilePictureException(int status, String message) {
        super(message);
        this.status = status;
    }

    public UserProfilePictureException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
