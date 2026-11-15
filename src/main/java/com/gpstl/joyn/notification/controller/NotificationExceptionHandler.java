package com.gpstl.joyn.notification.controller;

import com.gpstl.joyn.joboffer.dto.ApiError;
import com.gpstl.joyn.notification.exception.NotificationException;
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
 * Gestionnaire d'exceptions pour NotificationController, au format standard ApiError.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = NotificationController.class)
public class NotificationExceptionHandler {

    @ExceptionHandler(NotificationException.class)
    public ResponseEntity<ApiError> handleNotificationException(NotificationException ex) {
        return error(HttpStatus.valueOf(ex.getStatus()), ex.getField(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getName(), "Must be a valid value");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, null, "Malformed JSON body");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String field, String message) {
        List<ApiError.FieldError> details = field == null ? List.of() : List.of(new ApiError.FieldError(field, message));
        return ResponseEntity.status(status).body(new ApiError(status.value(), status.getReasonPhrase(), message, details));
    }
}
