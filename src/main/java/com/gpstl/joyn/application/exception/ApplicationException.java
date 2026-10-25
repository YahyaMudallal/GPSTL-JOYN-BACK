package com.gpstl.joyn.application.exception;

import org.springframework.http.HttpStatus;

public final class ApplicationException extends RuntimeException {

    private final HttpStatus status;

    private ApplicationException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static ApplicationException unauthorized(String message) {
        return new ApplicationException(HttpStatus.UNAUTHORIZED, message);
    }

    public static ApplicationException forbidden(String message) {
        return new ApplicationException(HttpStatus.FORBIDDEN, message);
    }

    public static ApplicationException notFound(String message) {
        return new ApplicationException(HttpStatus.NOT_FOUND, message);
    }

    public static ApplicationException conflict(String message) {
        return new ApplicationException(HttpStatus.CONFLICT, message);
    }

    public static ApplicationException badRequest(String message) {
        return new ApplicationException(HttpStatus.BAD_REQUEST, message);
    }

    public static ApplicationException internalError(String message) {
        return new ApplicationException(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    public static ApplicationException fromJobAccessFailure(HttpStatus status, String message) {
        return new ApplicationException(status, message);
    }
}
