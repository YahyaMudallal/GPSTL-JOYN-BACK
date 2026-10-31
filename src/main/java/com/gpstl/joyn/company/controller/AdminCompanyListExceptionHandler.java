package com.gpstl.joyn.company.controller;

import com.gpstl.joyn.company.exception.InvalidQueryParameterException;
import com.gpstl.joyn.joboffer.dto.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.util.List;

/**
 * Erreurs 400 de la liste admin des entreprises, au même format ApiError que R58 et U16.
 */
@RestControllerAdvice(assignableTypes = AdminCompanyListController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminCompanyListExceptionHandler {

    /** Valeur refusée par le service (ex. status=FOO, limit=500). */
    @ExceptionHandler(InvalidQueryParameterException.class)
    public ResponseEntity<ApiError> handleInvalidParameter(InvalidQueryParameterException ex) {
        return badRequest(ex.getParameter(), ex.getMessage());
    }

    /** Valeur du mauvais type (ex. page=abc). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return badRequest(ex.getName(), "Doit être un nombre entier");
    }

    private static ResponseEntity<ApiError> badRequest(String parameter, String message) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(new ApiError(status.value(), status.getReasonPhrase(),
                "Paramètres de recherche invalides", List.of(new ApiError.FieldError(parameter, message))));
    }
}
