package com.gpstl.joyn.recruiter.controller;

import com.gpstl.joyn.common.exception.DashboardException;
import com.gpstl.joyn.joboffer.dto.ApiError;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * Erreurs du dashboard recruteur (R49), au format ApiError commun.
 * Limité à RecruiterDashboardController ; priorité maximale pour passer avant
 * le gestionnaire global RegistrationExceptionHandler (qui attrape les IllegalArgumentException).
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = RecruiterDashboardController.class)
public class RecruiterDashboardExceptionHandler {

    @ExceptionHandler(DashboardException.class)
    public ResponseEntity<ApiError> handleDashboardError(DashboardException ex) {
        return error(HttpStatus.valueOf(ex.getStatus()), ex.getField(), ex.getMessage());
    }

    /** job_id=abc : pas un nombre. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getName(), "Doit être un nombre entier");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String field, String message) {
        List<ApiError.FieldError> errors = field == null
                ? List.of()
                : List.of(new ApiError.FieldError(field, message));
        return ResponseEntity.status(status)
                .body(new ApiError(status.value(), status.getReasonPhrase(), message, errors));
    }
}
