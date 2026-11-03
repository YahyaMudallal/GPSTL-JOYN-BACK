
package com.gpstl.joyn.company.controller;

import com.gpstl.joyn.company.dto.CompanyResponse;
import com.gpstl.joyn.company.service.CompanyService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
}
