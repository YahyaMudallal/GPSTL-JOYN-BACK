package com.gpstl.joyn.joboffer.exception;

import java.util.Map;

/** Transporte le statut HTTP et les erreurs par champ jusqu’au gestionnaire d’exceptions. */
public class JobApiException extends RuntimeException {
    private final int status;
    private final Map<String, String> errors;

    public JobApiException(int status, String message) {
        this(status, message, Map.of());
    }

    public JobApiException(int status, String message, Map<String, String> errors) {
        super(message);
        this.status = status;
        this.errors = Map.copyOf(errors);
    }

    public int getStatus() { return status; }
    public Map<String, String> getErrors() { return errors; }
}
