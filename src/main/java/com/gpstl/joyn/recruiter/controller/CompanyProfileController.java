
package com.gpstl.joyn.recruiter.controller;

import com.gpstl.joyn.recruiter.dto.CompanyProfileResponse;
import com.gpstl.joyn.recruiter.service.CompanyProfileService;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/companies")
public class CompanyProfileController {

    private final CompanyProfileService service;

    public CompanyProfileController(CompanyProfileService service) {
        this.service = service;
    }

    @GetMapping("/{companyId}")
    public ResponseEntity<CompanyProfileResponse> getCompany(
            @PathVariable Long companyId,
            @RequestParam(name = "include_jobs", defaultValue = "true")
            boolean includeJobs,
            Authentication authentication) {

        CompanyProfileResponse response =
                service.getProfile(companyId, includeJobs, authentication);

        return ResponseEntity.ok()
        .cacheControl(
                CacheControl.maxAge(300, TimeUnit.SECONDS)
                        .cachePublic()
        )
        .eTag("\"" + Integer.toHexString(response.hashCode()) + "\"")
        .body(response);
    }
}
