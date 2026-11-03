package com.gpstl.joyn.recruiter.controller;

import com.gpstl.joyn.recruiter.dto.RecruiterProfilePatch;
import com.gpstl.joyn.recruiter.dto.RecruiterProfileResponse;
import com.gpstl.joyn.recruiter.exception.RecruiterProfileException;
import com.gpstl.joyn.recruiter.service.RecruiterProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

/**
 * Profil recruteur (R78). Pas d'identifiant dans l'URL : le profil est celui du token,
 * un recruteur ne peut pas viser un tiers.
 * Le rôle est exigé dès SecurityConfig ; le service revérifie le compte en base.
 */
@RestController
@RequestMapping("/api/v1/recruiters")
@Tag(name = "Recruiters", description = "Permet aux recruteurs de s'enregistrer et de gérer les informations de leur profil")
@SecurityRequirement(name = "bearerAuth")
public class RecruiterProfileController {
    private final RecruiterProfileService profiles;

    public RecruiterProfileController(RecruiterProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping(value = "/profile", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "getRecruiterProfile", summary = "Retrieve the authenticated recruiter's profile",
        description = "Returns account credentials and affiliation data for the currently logged-in recruiter")
    @ApiResponse(responseCode = "200", description = "Recruiter profile")
    @ApiResponse(responseCode = "401", description = "Token missing, invalid or expired, or user no longer exists", content = @Content)
    @ApiResponse(responseCode = "403", description = "Role not allowed on this route, or account suspended", content = @Content)
    @ApiResponse(responseCode = "404", description = "Recruiter profile not found", content = @Content)
    public RecruiterProfileResponse get(Principal principal) {
        return profiles.getProfile(userId(principal));
    }

    /** Map plutôt qu'un DTO typé : distinguer un champ absent (conservé) d'un null explicite (refusé). */
    @PatchMapping(value = "/profile", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "updateRecruiterProfile", summary = "Update the name of the authenticated recruiter",
        description = "Absent field: kept. Any field other than firstName and lastName is rejected.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(schema = @Schema(implementation = RecruiterProfilePatch.class))))
    @ApiResponse(responseCode = "200", description = "Updated recruiter profile")
    @ApiResponse(responseCode = "400", description = "Invalid, unknown or protected fields; errors listed per field", content = @Content)
    @ApiResponse(responseCode = "401", description = "Token missing, invalid or expired, or user no longer exists", content = @Content)
    @ApiResponse(responseCode = "403", description = "Role not allowed on this route, or account suspended", content = @Content)
    @ApiResponse(responseCode = "404", description = "Recruiter profile not found", content = @Content)
    @ApiResponse(responseCode = "415", description = "Content-Type is not application/json", content = @Content)
    public RecruiterProfileResponse patch(@RequestBody(required = false) Map<String, Object> body, Principal principal) {
        return profiles.updateProfile(userId(principal), body);
    }

    /** users.id vient du claim « sub » du token vérifié, jamais du corps JSON. */
    private static long userId(Principal principal) {
        if (principal == null) throw new RecruiterProfileException(401, "Authentication is required");
        try {
            long id = Long.parseLong(principal.getName());
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException ex) {
            throw new RecruiterProfileException(401, "Authentication must provide the database user ID");
        }
    }
}
