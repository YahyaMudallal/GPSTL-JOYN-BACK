package com.gpstl.joyn.joboffer.dto;

import java.util.List;

/**
 * Format commun des réponses d'erreur.
 * Exemple (400) :
 * {"status":400,"error":"Bad Request","message":"La requête contient des erreurs",
 *  "errors":[{"field":"title","message":"Le titre est obligatoire"}]}
 */
public record ApiError(int status, String error, String message, List<FieldError> errors) {

    public record FieldError(String field, String message) {
    }
}
