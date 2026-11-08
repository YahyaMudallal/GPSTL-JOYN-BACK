package com.gpstl.joyn.company.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Corps d'erreur de la recherche SIRET (R77). */
public record SiretLookupError(
        @Schema(description = "Code stable : INVALID_SIRET, SIRET_NOT_FOUND ou SIRET_SERVICE_UNAVAILABLE",
                example = "SIRET_NOT_FOUND")
        String code,
        @Schema(description = "Message lisible destiné à l'utilisateur")
        String message) {
}
