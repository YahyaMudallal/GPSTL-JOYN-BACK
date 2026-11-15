
package com.gpstl.joyn.company.controller;

import com.gpstl.joyn.company.dto.CompanyResponse;
import com.gpstl.joyn.company.service.CompanyService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/companies")
@Tag(name = "Companies", description = "Consultation publique des entreprises et la mise à jour de leurs données par les admins ou par les recruteurs habilités")
public class CompanyController {

    private final CompanyService service;

    public CompanyController(CompanyService service) {
        this.service = service;
    }

    @GetMapping("/{companyId}")
    @Operation(
        summary = "Retrieve public profile and active jobs of a company",
        description = "Returns company identity, description, address, and optionally all published job openings")
    public ResponseEntity<CompanyResponse> getCompany(
            @PathVariable Long companyId,
            @RequestParam(name = "include_jobs", defaultValue = "true")
            boolean includeJobs,
            Authentication authentication) {

        CompanyResponse response =
                service.getProfile(companyId, includeJobs, authentication);

        return ResponseEntity.ok()
        .cacheControl(
                CacheControl.maxAge(300, TimeUnit.SECONDS)
                        .cachePublic()
        )
        .eTag("\"" + Integer.toHexString(response.hashCode()) + "\"")
        .body(response);
    }
    
    @PatchMapping("/{companyId}")
    @Operation(
        summary = "Update company information",
        description = "Updates general corporate details such as address, size, or website. Restricted to affiliated recruiters or administrators")
    public ResponseEntity<CompanyResponse> updateCompany(
            @PathVariable Long companyId,
            @RequestBody java.util.Map<String, Object> changes,
            Authentication authentication) {

        CompanyResponse response =
                service.updateProfile(companyId, changes, authentication);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{companyId}")
    @Operation(
        summary = "Soft delete a company and close its active jobs",
        description = "Marks the company as DELETED, closes all its active job offers and revokes the rights of its recruiters, "
                + "while preserving applications, conversations and messages. Restricted to administrators or recruiters of the company")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Company deleted; no response body"),
        @ApiResponse(responseCode = "400", description = "Invalid request body"),
        @ApiResponse(responseCode = "401", description = "Trusted authentication is missing or invalid"),
        @ApiResponse(responseCode = "403", description = "Role or company does not permit this action"),
        @ApiResponse(responseCode = "404", description = "Company does not exist or is already deleted"),
        @ApiResponse(responseCode = "500", description = "Internal error; transaction rolled back")
    })
    public ResponseEntity<Void> deleteCompany(
            @PathVariable Long companyId,
            @RequestBody(required = false) Map<String, Object> body,
            Authentication authentication) {

        String reason = null;

        if (body != null) {
            if (body.keySet().stream().anyMatch(key -> !key.equals("reason"))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Champ inconnu dans la requête.");
            }

            Object raw = body.get("reason");

            if (raw != null && (!(raw instanceof String) || ((String) raw).length() > 2000)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "reason doit être une chaîne d'au plus 2000 caractères.");
            }

            reason = (String) raw;
        }

        service.deleteCompany(companyId, reason, authentication);
        return ResponseEntity.noContent().build();
    }
}
