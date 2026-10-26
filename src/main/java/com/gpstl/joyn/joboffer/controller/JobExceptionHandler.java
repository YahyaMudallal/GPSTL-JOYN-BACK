package com.gpstl.joyn.joboffer.controller;

import com.gpstl.joyn.joboffer.exception.JobApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

/** Convertit les exceptions des endpoints de gestion en réponses HTTP JSON cohérentes. */
@RestControllerAdvice(assignableTypes = JobManagementEndpoint.class)
public class JobExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(JobExceptionHandler.class);

    @ExceptionHandler(JobApiException.class)
    public ResponseEntity<?> jobError(JobApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of("message", ex.getMessage(), "errors", ex.getErrors()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<?> malformed(Exception ex) {
        return ResponseEntity.badRequest().body(Map.of("message", "Invalid JSON body or jobId", "errors", Map.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected(Exception ex) {
        // Erreurs du framework (415 mauvais Content-Type, 405 méthode...) : garder leur statut, pas 500.
        if (ex instanceof ErrorResponse framework) {
            String detail = framework.getBody().getDetail();
            return ResponseEntity.status(framework.getStatusCode())
                .body(Map.of("message", detail == null ? "" : detail, "errors", Map.of()));
        }
        LOG.error("Job operation failed", ex);
        return ResponseEntity.internalServerError().body(Map.of("message", "Internal server error", "errors", Map.of()));
    }
}
