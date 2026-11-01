
package com.gpstl.joyn.company.controller;

import com.gpstl.joyn.company.dto.CompanyResponse;
import com.gpstl.joyn.company.service.CompanyService;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/companies")
public class CompanyController {

    private final CompanyService service;

    public CompanyController(CompanyService service) {
        this.service = service;
    }

    @GetMapping("/{companyId}")
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
    public ResponseEntity<CompanyResponse> updateCompany(
            @PathVariable Long companyId,
            @RequestBody java.util.Map<String, Object> changes,
            Authentication authentication) {

        CompanyResponse response =
                service.updateProfile(companyId, changes, authentication);

        return ResponseEntity.ok(response);
    }
}
