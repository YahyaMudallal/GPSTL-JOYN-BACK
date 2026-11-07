package com.gpstl.joyn.candidate.controller;

import com.gpstl.joyn.common.exception.DashboardException;
import com.gpstl.joyn.joboffer.dto.ApiError;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Erreurs du dashboard candidat (CA36), au format ApiError commun.
 * Limité à CandidateDashboardController ; priorité maximale pour passer avant
 * le gestionnaire global RegistrationExceptionHandler.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = CandidateDashboardController.class)
public class CandidateDashboardExceptionHandler {

    @ExceptionHandler(DashboardException.class)
    public ResponseEntity<ApiError> handleDashboardError(DashboardException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatus());
        List<ApiError.FieldError> errors = ex.getField() == null
                ? List.of()
                : List.of(new ApiError.FieldError(ex.getField(), ex.getMessage()));
        return ResponseEntity.status(status)
                .body(new ApiError(status.value(), status.getReasonPhrase(), ex.getMessage(), errors));
    }
}
