package com.gpstl.joyn.candidate.controller;

import com.gpstl.joyn.company.exception.InvalidQueryParameterException;
import com.gpstl.joyn.joboffer.dto.ApiError;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDate;
import java.util.List;

/**
 * Erreurs 400 de la liste admin des candidats, au même format ApiError que la liste des entreprises (A66).
 */
@RestControllerAdvice(assignableTypes = AdminCandidateListController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminCandidateListExceptionHandler {

    /** Valeur refusée par le service (ex. status=FOO, limit=500). */
    @ExceptionHandler(InvalidQueryParameterException.class)
    public ResponseEntity<ApiError> handleInvalidParameter(InvalidQueryParameterException ex) {
        return badRequest(ex.getParameter(), ex.getMessage());
    }

    /** Valeur du mauvais type (ex. page=abc, has_resume=peut-etre, created_from=hier). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message;
        if (Boolean.class.equals(ex.getRequiredType())) {
            message = "Doit valoir true ou false";
        } else if (LocalDate.class.equals(ex.getRequiredType())) {
            message = "Doit être une date au format AAAA-MM-JJ";
        } else {
            message = "Doit être un nombre entier";
        }
        return badRequest(ex.getName(), message);
    }

    private static ResponseEntity<ApiError> badRequest(String parameter, String message) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(new ApiError(status.value(), status.getReasonPhrase(),
                "Paramètres de recherche invalides", List.of(new ApiError.FieldError(parameter, message))));
    }
}
