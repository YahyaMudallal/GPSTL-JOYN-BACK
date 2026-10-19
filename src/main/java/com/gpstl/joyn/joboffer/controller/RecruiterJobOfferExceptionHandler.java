package com.gpstl.joyn.joboffer.controller;

import com.gpstl.joyn.joboffer.dto.ApiError;
import com.gpstl.joyn.joboffer.exception.InvalidFieldException;
import com.gpstl.joyn.joboffer.exception.RecruiterProfileNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Transforme les erreurs de la route de création d'offre en réponses JSON lisibles.
 * Limité à RecruiterJobOfferController pour ne pas modifier le comportement des autres routes ;
 * l'équipe pourra l'élargir à toute l'API si ce format convient à tout le monde.
 */
@RestControllerAdvice(assignableTypes = RecruiterJobOfferController.class)
public class RecruiterJobOfferExceptionHandler {

    /** 400 : un ou plusieurs champs ne respectent pas les règles (@NotBlank, @Pattern...). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiError.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ApiError.FieldError(toJsonName(e.getField()), e.getDefaultMessage()))
                .toList();
        return badRequest("La requête contient des erreurs", errors);
    }

    /** 400 : erreur détectée par le service (ex. type de contrat inconnu). */
    @ExceptionHandler(InvalidFieldException.class)
    public ResponseEntity<ApiError> handleInvalidField(InvalidFieldException ex) {
        return badRequest("La requête contient des erreurs",
                List.of(new ApiError.FieldError(ex.getField(), ex.getMessage())));
    }

    /** 400 : JSON mal formé ou valeur du mauvais type (ex. date invalide, texte à la place d'un nombre). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return badRequest("Le corps de la requête n'est pas un JSON valide ou contient une valeur du mauvais type",
                List.of());
    }

    /** 403 : compte recruteur sans profil recruteur rattaché à une entreprise. */
    @ExceptionHandler(RecruiterProfileNotFoundException.class)
    public ResponseEntity<ApiError> handleNoRecruiterProfile(RecruiterProfileNotFoundException ex) {
        HttpStatus status = HttpStatus.FORBIDDEN;
        return ResponseEntity.status(status)
                .body(new ApiError(status.value(), status.getReasonPhrase(), ex.getMessage(), List.of()));
    }

    private static ResponseEntity<ApiError> badRequest(String message, List<ApiError.FieldError> errors) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(new ApiError(status.value(), status.getReasonPhrase(), message, errors));
    }

    /**
     * Les erreurs portent le nom Java du champ (salaryMax) : on renvoie le nom JSON (salary_max)
     * pour que le front sache quel champ du formulaire est en erreur.
     */
    static String toJsonName(String javaField) {
        if ("salaryRangeValid".equals(javaField)) {
            return "salary_max";
        }
        if (javaField.startsWith("skills")) {
            return "skills"; // erreur sur un élément de la liste : skills[1] -> skills
        }
        return javaField.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
