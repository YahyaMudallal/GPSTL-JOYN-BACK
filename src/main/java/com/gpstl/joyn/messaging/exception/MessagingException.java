package com.gpstl.joyn.messaging.exception;

/**
 * Erreur de la messagerie (CO26), renvoyée au client avec son code HTTP au format ApiError.
 * field = nom du champ ou du paramètre en cause (ex. "text", "application_id"), ou null.
 */
public class MessagingException extends RuntimeException {

    private final int status;
    private final String field;

    public MessagingException(int status, String field, String message) {
        super(message);
        this.status = status;
        this.field = field;
    }

    public static MessagingException badRequest(String field, String message) {
        return new MessagingException(400, field, message);
    }

    public static MessagingException unauthorized(String message) {
        return new MessagingException(401, null, message);
    }

    public static MessagingException forbidden(String message) {
        return new MessagingException(403, null, message);
    }

    public static MessagingException notFound(String field, String message) {
        return new MessagingException(404, field, message);
    }

    public static MessagingException conflict(String field, String message) {
        return new MessagingException(409, field, message);
    }

    public int getStatus() {
        return status;
    }

    public String getField() {
        return field;
    }
}
