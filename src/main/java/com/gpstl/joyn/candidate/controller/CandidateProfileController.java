package com.gpstl.joyn.candidate.controller;

import com.gpstl.joyn.candidate.dto.CandidateProfileResponse;
import com.gpstl.joyn.candidate.exception.CandidateProfileException;
import com.gpstl.joyn.candidate.service.CandidateProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

/**
 * Profil candidat (CA56 lecture, CA68 mise à jour partielle).
 *  - /profile : le candidat lit et modifie SON profil. Pas d'identifiant dans l'URL : le profil est
 *    celui du token, un candidat ne peut pas viser un tiers.
 *  - /{candidateId}/profile : lecture seule pour les recruteurs et administrateurs. Seul le candidat
 *    peut modifier son profil, il n'existe donc pas de PATCH sur cette route.
 * Les rôles sont exigés dès SecurityConfig ; le service revérifie le compte en base.
 */
@RestController
@RequestMapping("/api/v1/candidates")
@Tag(name = "Candidate profile", description = "Candidate profile; subject is users.id")
@SecurityRequirement(name = "bearerAuth")
// Sans « content » explicite, springdoc décrirait les erreurs avec le type de retour (le profil).
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "Token missing, invalid or expired, or user no longer exists",
        content = @Content(schema = @Schema(ref = CandidateProfileController.SPEC + "Error"))),
    @ApiResponse(responseCode = "403", description = "Role not allowed on this route, or account suspended",
        content = @Content(schema = @Schema(ref = CandidateProfileController.SPEC + "Error"))),
    @ApiResponse(responseCode = "404", description = "Candidate profile not found (or hidden from recruiters)",
        content = @Content(schema = @Schema(ref = CandidateProfileController.SPEC + "Error"))),
    @ApiResponse(responseCode = "500", description = "Internal error; transaction rolled back",
        content = @Content(schema = @Schema(ref = CandidateProfileController.SPEC + "Error")))
})
public class CandidateProfileController {
    /** Schémas détaillés, servis comme fichier statique et résolus par Swagger UI. */
    static final String SPEC = "/openapi/candidate-profile.yaml#/components/schemas/";

    private final CandidateProfileService profiles;

    public CandidateProfileController(CandidateProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping(value = "/profile", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "getCandidateProfile", summary = "Get the full profile of the authenticated candidate")
    @ApiResponse(responseCode = "200", description = "Candidate profile", content =
        @Content(schema = @Schema(ref = SPEC + "CandidateProfile")))
    public CandidateProfileResponse get(Principal principal) {
        return profiles.getProfile(userId(principal));
    }

    /**
     * Profil du candidat {candidateId} (candidate_profiles.id) vu par un recruteur ou un administrateur.
     * Le recruteur reçoit une vue filtrée par les réglages de visibilité du candidat ; l'administrateur, la vue complète.
     */
    @GetMapping(value = "/{candidateId}/profile", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "getCandidateProfileById", summary = "Get a given candidate's profile (recruiter or administrator)",
        description = "Recruiters get a view filtered by the candidate's visibility settings; administrators get the full profile.")
    @ApiResponse(responseCode = "200", description = "Candidate profile", content =
        @Content(schema = @Schema(ref = SPEC + "CandidateProfile")))
    @ApiResponse(responseCode = "400", description = "candidateId is not a positive integer",
        content = @Content(schema = @Schema(ref = SPEC + "Error")))
    public CandidateProfileResponse getById(@PathVariable long candidateId, Principal principal) {
        if (candidateId <= 0) throw new CandidateProfileException(400, "candidateId must be a positive integer");
        return profiles.getProfileById(userId(principal), candidateId);
    }

    /**
     * Map plutôt qu'un DTO typé : distinguer un champ absent (conservé) d'un null explicite (effacé).
     * springdoc ne peut pas décrire une Map : le schéma détaillé vient de candidate-profile.yaml.
     */
    @PatchMapping(value = "/profile", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "updateCandidateProfile", summary = "Partially update the profile of the authenticated candidate",
        description = "Absent field: kept. Explicit null: cleared. Nested objects are merged field by field; "
            + "lists (skills, experiences, educations, preferences.contract_types) are replaced. "
            + "Full rules: /openapi/candidate-profile.yaml",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(schema = @Schema(ref = SPEC + "CandidateProfilePatch"))))
    @ApiResponse(responseCode = "200", description = "Updated candidate profile", content =
        @Content(schema = @Schema(ref = SPEC + "CandidateProfile")))
    @ApiResponse(responseCode = "400", description = "Invalid, unknown or protected fields; errors listed per field",
        content = @Content(schema = @Schema(ref = SPEC + "Error")))
    @ApiResponse(responseCode = "415", description = "Content-Type is not application/json", content = @Content)
    public CandidateProfileResponse patch(@RequestBody(required = false) Map<String, Object> body,
            Principal principal) {
        return profiles.updateProfile(userId(principal), body);
    }

    /** users.id vient du claim « sub » du token vérifié, jamais du corps JSON. */
    private static long userId(Principal principal) {
        if (principal == null) throw new CandidateProfileException(401, "Authentication is required");
        try {
            long id = Long.parseLong(principal.getName());
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException ex) {
            throw new CandidateProfileException(401, "Authentication must provide the database user ID");
        }
    }
}
