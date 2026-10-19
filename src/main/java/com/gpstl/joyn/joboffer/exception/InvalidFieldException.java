package com.gpstl.joyn.joboffer.exception;

/**
 * Erreur de donnée détectée par le service (ex. type de contrat inconnu en base).
 * Renvoyée au client en 400 avec le nom du champ concerné.
 */
public class InvalidFieldException extends RuntimeException {

    private final String field;

    public InvalidFieldException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
