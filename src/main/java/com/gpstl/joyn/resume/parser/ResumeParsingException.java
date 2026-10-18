package com.gpstl.joyn.resume.parser;

/**
 * Exception thrown when there is an error during the resume parsing process.
 */
public class ResumeParsingException extends RuntimeException {

    public ResumeParsingException(String message) {
        super(message);
    }

    public ResumeParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
