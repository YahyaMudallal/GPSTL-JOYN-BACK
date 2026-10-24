package com.gpstl.joyn.candidate.controller;

import com.gpstl.joyn.candidate.exception.CandidateProfileException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Réponses JSON cohérentes ({ "message", "errors" }) pour les routes du profil candidat.
 * Limité aux contrôleurs du profil (candidat et admin) ; priorité la plus haute pour que le
 * gestionnaire global RegistrationExceptionHandler ne réponde pas à sa place.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {CandidateProfileController.class, AdminCandidateProfileController.class})
public class CandidateProfileExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(CandidateProfileExceptionHandler.class);

    @ExceptionHandler(CandidateProfileException.class)
    public ResponseEntity<Map<String, Object>> profileError(CandidateProfileException ex) {
        return ResponseEntity.status(ex.getStatus()).body(body(ex.getMessage(), ex.getErrors()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> malformed(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(body("Request body must be a valid JSON object", Map.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(Exception ex) {
        // Erreurs du framework (415 mauvais Content-Type, 405 méthode...) : garder leur statut, pas 500.
        if (ex instanceof ErrorResponse framework) {
            return ResponseEntity.status(framework.getStatusCode())
                    .body(body(framework.getBody().getDetail(), Map.of()));
        }
        LOG.error("Candidate profile operation failed", ex);
        return ResponseEntity.internalServerError().body(body("Internal server error", Map.of()));
    }

    private static Map<String, Object> body(String message, Map<String, String> errors) {
        return Map.of("message", message == null ? "" : message, "errors", errors);
    }
}
