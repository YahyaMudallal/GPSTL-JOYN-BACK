package com.gpstl.joyn.notification.exception;

/**
 * Exception spécifique au module de notifications, renvoyée au client sous le format standard ApiError.
 */
public class NotificationException extends RuntimeException {

    private final int status;
    private final String field;

    public NotificationException(int status, String field, String message) {
        super(message);
        this.status = status;
        this.field = field;
    }

    public static NotificationException badRequest(String field, String message) {
        return new NotificationException(400, field, message);
    }

    public static NotificationException unauthorized(String message) {
        return new NotificationException(401, null, message);
    }

    public static NotificationException forbidden(String message) {
        return new NotificationException(403, null, message);
    }

    public static NotificationException notFound(String field, String message) {
        return new NotificationException(404, field, message);
    }

    public int getStatus() {
        return status;
    }

    public String getField() {
        return field;
    }
}
