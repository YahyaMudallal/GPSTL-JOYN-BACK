package com.gpstl.joyn.messaging.controller;

import com.gpstl.joyn.joboffer.dto.ApiError;
import com.gpstl.joyn.messaging.exception.MessagingException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * Erreurs de la messagerie (CO26), au format ApiError commun (comme R58, U16, A66, CA36, R49).
 * Limité à ConversationController ; priorité maximale pour passer avant
 * le gestionnaire global RegistrationExceptionHandler.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ConversationController.class)
public class MessagingExceptionHandler {

    @ExceptionHandler(MessagingException.class)
    public ResponseEntity<ApiError> handleMessagingError(MessagingException ex) {
        return error(HttpStatus.valueOf(ex.getStatus()), ex.getField(), ex.getMessage());
    }

    /** Ex. /conversations/abc/messages ou ?limit=abc. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getName(), "Must be an integer");
    }

    /** Corps JSON illisible (JSON mal formé). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, null, "Malformed JSON body");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String field, String message) {
        List<ApiError.FieldError> errors = field == null
                ? List.of()
                : List.of(new ApiError.FieldError(field, message));
        return ResponseEntity.status(status)
                .body(new ApiError(status.value(), status.getReasonPhrase(), message, errors));
    }
}
