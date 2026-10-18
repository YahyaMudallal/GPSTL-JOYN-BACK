package com.gpstl.joyn.resume.service;

/**
 * Exception thrown when a resume is invalid.
 */
public class InvalidResumeException extends RuntimeException {

    public InvalidResumeException(String message) {
        super(message);
    }

    public InvalidResumeException(String message, Throwable cause) {
        super(message, cause);
    }
}
