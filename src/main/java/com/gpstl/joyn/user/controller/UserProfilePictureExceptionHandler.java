package com.gpstl.joyn.user.controller;

import com.gpstl.joyn.user.exception.UserProfilePictureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

/**
 * Gestionnaire d'exceptions pour les endpoints de gestion de la photo de profil.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {
        UserProfilePictureController.class,
        LocalUserProfilePictureController.class
})
public class UserProfilePictureExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(UserProfilePictureExceptionHandler.class);

    @ExceptionHandler(UserProfilePictureException.class)
    public ResponseEntity<Map<String, Object>> handleProfilePictureException(UserProfilePictureException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxSizeException(MaxUploadSizeExceededException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", "La taille du fichier dépasse la limite maximale autorisée (5 Mo)."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpectedException(Exception ex) {
        log.error("Erreur inattendue lors de la manipulation d'une photo de profil", ex);
        return ResponseEntity.internalServerError().body(Map.of("message", "Erreur interne du serveur"));
    }
}
