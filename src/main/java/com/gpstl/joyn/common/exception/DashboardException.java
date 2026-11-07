package com.gpstl.joyn.common.exception;

/**
 * Erreur d'un tableau de bord (CA36, R49), renvoyée au client avec son code HTTP.
 * field = nom du paramètre en cause (ex. "period", "job_id"), ou null s'il n'y en a pas.
 */
public class DashboardException extends RuntimeException {

    private final int status;
    private final String field;

    public DashboardException(int status, String field, String message) {
        super(message);
        this.status = status;
        this.field = field;
    }

    public static DashboardException badRequest(String field, String message) {
        return new DashboardException(400, field, message);
    }

    public static DashboardException unauthorized(String message) {
        return new DashboardException(401, null, message);
    }

    public static DashboardException forbidden(String message) {
        return new DashboardException(403, null, message);
    }

    public static DashboardException notFound(String field, String message) {
        return new DashboardException(404, field, message);
    }

    public int getStatus() {
        return status;
    }

    public String getField() {
        return field;
    }
}
