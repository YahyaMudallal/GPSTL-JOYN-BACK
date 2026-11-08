package com.gpstl.joyn.company.controller;

import com.gpstl.joyn.company.dto.SiretLookupResponse;
import com.gpstl.joyn.company.service.SiretLookupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * R77 – Recherche publique d'une entreprise par SIRET pour le formulaire d'inscription.
 * Route alignée sur le frontend : GET /api/v1/companies/lookup?siret=...
 */
@RestController
@RequestMapping("/api/v1/companies")
@Tag(name = "Companies", description = "Consultation publique des entreprises et la mise à jour de leurs données par les admins ou par les recruteurs habilités")
@CrossOrigin(origins = "*")
public class SiretLookupController {
    private final SiretLookupService service;

    public SiretLookupController(SiretLookupService service) {
        this.service = service;
    }

    @Operation(summary = "Look up public company information by SIRET",
            description = "Recherche les informations publiques d'une entreprise (API Recherche d'entreprises) à partir de son SIRET, sans authentification")
    @ApiResponse(responseCode = "200", description = "SIRET trouvé, y compris si l'établissement est fermé")
    @ApiResponse(responseCode = "400", description = "Format du SIRET incorrect")
    @ApiResponse(responseCode = "404", description = "SIRET non trouvé dans les données publiques")
    @ApiResponse(responseCode = "503", description = "Service externe indisponible")
    @GetMapping("/lookup")
    public ResponseEntity<SiretLookupResponse> lookup(
            // Non obligatoire côté Spring : l'absence est traitée par le service (400 INVALID_SIRET).
            @Parameter(description = "SIRET de 14 chiffres, sans espaces", example = "35600000000048")
            @RequestParam(required = false) String siret) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.lookup(siret));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleLookupError(ResponseStatusException exception) {
        String code = switch (exception.getStatusCode().value()) {
            case 400 -> "INVALID_SIRET";
            case 404 -> "SIRET_NOT_FOUND";
            default -> "SIRET_SERVICE_UNAVAILABLE";
        };
        return ResponseEntity.status(exception.getStatusCode()).cacheControl(CacheControl.noStore())
                .body(Map.of("code", code, "message", exception.getReason()));
    }
}
