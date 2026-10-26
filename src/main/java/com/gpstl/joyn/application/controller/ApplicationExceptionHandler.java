package com.gpstl.joyn.application.controller;

import com.gpstl.joyn.application.dto.ApplicationErrorResponse;
import com.gpstl.joyn.application.dto.ApplicationErrorResponse.FieldError;
import com.gpstl.joyn.application.exception.ApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice(assignableTypes = ApplicationController.class)
public class ApplicationExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationExceptionHandler.class);

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApplicationErrorResponse> handleApplicationException(ApplicationException exception) {
        return error(exception.getStatus(), exception.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApplicationErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        List<FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new FieldError(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();
        return error(HttpStatus.BAD_REQUEST, "Request validation failed", errors);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApplicationErrorResponse> handleMalformedRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "Request body or path parameter is invalid", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApplicationErrorResponse> handleUnexpectedException(Exception exception) {
        // Framework errors (415 wrong Content-Type, 405 wrong method, ...) keep their own status
        // instead of being reported as 500.
        if (exception instanceof ErrorResponse framework) {
            HttpStatus status = HttpStatus.valueOf(framework.getStatusCode().value());
            return error(status, framework.getBody().getDetail(), List.of());
        }
        LOG.error("Application endpoint failed unexpectedly", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", List.of());
    }

    private static ResponseEntity<ApplicationErrorResponse> error(
            HttpStatus status,
            String message,
            List<FieldError> errors) {
        ApplicationErrorResponse body = new ApplicationErrorResponse(
                status.value(), status.getReasonPhrase(), message, errors);
        return ResponseEntity.status(status).body(body);
    }
}
