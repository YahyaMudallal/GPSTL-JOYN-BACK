package com.gpstl.joyn.joboffer.controller;

import com.gpstl.joyn.joboffer.dto.ApiError;
import com.gpstl.joyn.joboffer.dto.CreateJobOfferRequest;
import com.gpstl.joyn.joboffer.dto.JobOfferResponse;
import com.gpstl.joyn.joboffer.exception.RecruiterProfileNotFoundException;
import com.gpstl.joyn.joboffer.service.JobOfferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Routes réservées aux recruteurs pour gérer leurs offres.
 * L'accès (token valide + rôle RECRUITER) est contrôlé dans SecurityConfig.
 */
@RestController
@RequestMapping("/api/v1/recruiters/jobs")
@Tag(name = "Offres (recruteur)", description = "Gestion des offres par le recruteur connecté")
@SecurityRequirement(name = "bearerAuth")
public class RecruiterJobOfferController {

    private final JobOfferService jobOfferService;

    public RecruiterJobOfferController(JobOfferService jobOfferService) {
        this.jobOfferService = jobOfferService;
    }

    @PostMapping(consumes = "application/json", produces = "application/json")
    @Operation(
            summary = "Créer une offre",
            description = "Crée une offre pour l'entreprise du recruteur connecté. "
                    + "L'entreprise et le recruteur sont déduits du token JWT, jamais du corps de la requête.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(examples = @ExampleObject(name = "Alternance", value = """
                            {
                              "title": "Développeur·se Full-Stack en alternance",
                              "contract_type": "APPRENTICESHIP",
                              "location": "Paris 9e",
                              "remote_policy": "HYBRID",
                              "experience_level": "JUNIOR",
                              "description": "Rejoins l'équipe Paiements pour construire le nouveau tableau de bord marchands.",
                              "salary_min": 1450,
                              "salary_max": 1600,
                              "currency": "EUR",
                              "salary_period": "MONTH",
                              "skills": ["React", "TypeScript", "PostgreSQL"],
                              "rhythm_type": "3 sem / 1 sem",
                              "start_date": "2027-09-01",
                              "duration_months": 24,
                              "degree_level_required": "BAC_5"
                            }"""))))
    @ApiResponse(responseCode = "201", description = "Offre créée",
            content = @Content(schema = @Schema(implementation = JobOfferResponse.class)))
    @ApiResponse(responseCode = "400", description = "Données manquantes ou invalides (détail par champ)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Token absent, expiré ou invalide", content = @Content)
    @ApiResponse(responseCode = "403", description = "L'utilisateur n'est pas recruteur", content = @Content)
    @ApiResponse(responseCode = "500", description = "Erreur interne", content = @Content)
    public ResponseEntity<JobOfferResponse> createJobOffer(@AuthenticationPrincipal Jwt jwt,
                                                           @Valid @RequestBody CreateJobOfferRequest request) {
        Long userId = parseUserId(jwt.getSubject());
        JobOfferResponse created = jobOfferService.createJobOffer(userId, request);
        return ResponseEntity
                .created(URI.create("/api/v1/recruiters/jobs/" + created.id()))
                .body(created);
    }

    private static Long parseUserId(String subject) {
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException | NullPointerException e) {
            throw new RecruiterProfileNotFoundException(subject);
        }
    }
}
