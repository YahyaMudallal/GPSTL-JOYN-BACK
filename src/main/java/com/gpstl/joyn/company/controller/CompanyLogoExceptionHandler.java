package com.gpstl.joyn.company.controller;

import com.gpstl.joyn.company.exception.CompanyLogoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Erreurs des routes du logo (R84), au même format que les photos de profil (U86) :
 * {"message": "..."} avec le bon code HTTP.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {CompanyLogoController.class, LocalCompanyLogoController.class})
public class CompanyLogoExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CompanyLogoExceptionHandler.class);

    @ExceptionHandler(CompanyLogoException.class)
    public ResponseEntity<Map<String, Object>> handleLogoException(CompanyLogoException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("message", "La taille du fichier dépasse la limite maximale autorisée (5 Mo)."));
    }

    /** Ex. /api/v1/companies/abc/logo. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("message", "L'identifiant de l'entreprise doit être un entier positif."));
    }

    /** 404 du mode local (logo introuvable) et 503 quand le stockage n'est pas configuré. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException ex) {
        String message = ex.getReason() == null ? "Erreur" : ex.getReason();
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message", message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("Erreur inattendue lors de la gestion d'un logo d'entreprise", ex);
        return ResponseEntity.internalServerError().body(Map.of("message", "Erreur interne du serveur"));
    }
}
