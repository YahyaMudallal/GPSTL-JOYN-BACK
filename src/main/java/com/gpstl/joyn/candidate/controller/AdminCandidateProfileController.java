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
 * Intervention d'un administrateur sur le profil d'un candidat désigné (CA68, cas admin).
 * Route distincte de /api/v1/candidates/profile : la route du candidat ne lit jamais d'identifiant
 * venant du client, ce qui garantit qu'un candidat ne peut pas viser le profil d'un tiers.
 */
@RestController
@RequestMapping("/api/v1/admin/candidates/{candidateId}/profile")
@Tag(name = "Admin - Candidate profile", description = "Administrator access to a given candidate profile")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "Token missing, invalid or expired, or user no longer exists",
        content = @Content(schema = @Schema(ref = CandidateProfileController.SPEC + "Error"))),
    @ApiResponse(responseCode = "403", description = "Not an administrator, or account suspended",
        content = @Content(schema = @Schema(ref = CandidateProfileController.SPEC + "Error"))),
    @ApiResponse(responseCode = "404", description = "No candidate profile with this id",
        content = @Content(schema = @Schema(ref = CandidateProfileController.SPEC + "Error"))),
    @ApiResponse(responseCode = "500", description = "Internal error; transaction rolled back",
        content = @Content(schema = @Schema(ref = CandidateProfileController.SPEC + "Error")))
})
public class AdminCandidateProfileController {
    private final CandidateProfileService profiles;

    public AdminCandidateProfileController(CandidateProfileService profiles) {
        this.profiles = profiles;
    }

    /**
     * Profil complet du candidat {candidateId}, au même format que GET /api/v1/candidates/profile.
     * L'administrateur est identifié par le « sub » du token ; le profil visé, par l'URL.
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "getCandidateProfileAsAdmin", summary = "Get the profile of a given candidate")
    @ApiResponse(responseCode = "200", description = "Candidate profile", content =
        @Content(schema = @Schema(ref = CandidateProfileController.SPEC + "CandidateProfile")))
    public CandidateProfileResponse get(@PathVariable long candidateId, Principal principal) {
        return profiles.getProfileAsAdmin(CandidateProfileController.userId(principal), validId(candidateId));
    }

    /**
     * Mise à jour partielle du candidat {candidateId} : même corps et mêmes règles que la route du candidat
     * (champs du compte toujours protégés). Le service journalise l'administrateur et les champs envoyés.
     */
    @PatchMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "updateCandidateProfileAsAdmin",
        summary = "Partially update the profile of a given candidate (audited)",
        description = "Same body and rules as PATCH /api/v1/candidates/profile. "
            + "The administrator and the modified fields are recorded in candidate_profile_audit.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(schema = @Schema(ref = CandidateProfileController.SPEC + "CandidateProfilePatch"))))
    @ApiResponse(responseCode = "200", description = "Updated candidate profile", content =
        @Content(schema = @Schema(ref = CandidateProfileController.SPEC + "CandidateProfile")))
    @ApiResponse(responseCode = "400", description = "Invalid, unknown or protected fields; errors listed per field",
        content = @Content(schema = @Schema(ref = CandidateProfileController.SPEC + "Error")))
    @ApiResponse(responseCode = "415", description = "Content-Type is not application/json", content = @Content)
    public CandidateProfileResponse patch(@PathVariable long candidateId,
            @RequestBody(required = false) Map<String, Object> body, Principal principal) {
        return profiles.updateProfileAsAdmin(CandidateProfileController.userId(principal), validId(candidateId), body);
    }

    /** Un id nul ou négatif ne peut désigner aucun profil : 400 plutôt qu'une recherche inutile en base. */
    private static long validId(long candidateId) {
        if (candidateId <= 0) throw new CandidateProfileException(400, "candidateId must be a positive integer");
        return candidateId;
    }
}
