package com.gpstl.joyn.company.exception;

/**
 * Paramètre de recherche invalide (ex. page=0, status=FOO). Renvoyée au client en 400,
 * avec le nom du paramètre concerné.
 */
public class InvalidQueryParameterException extends RuntimeException {

    private final String parameter;

    public InvalidQueryParameterException(String parameter, String message) {
        super(message);
        this.parameter = parameter;
    }

    public String getParameter() {
        return parameter;
    }
}
