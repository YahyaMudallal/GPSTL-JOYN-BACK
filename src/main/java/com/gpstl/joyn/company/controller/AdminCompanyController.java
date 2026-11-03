package com.gpstl.joyn.company.controller;

import com.gpstl.joyn.company.dto.CompanyResponse;
import com.gpstl.joyn.company.dto.CompanyStatusUpdateRequest;
import com.gpstl.joyn.company.service.CompanyService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/companies")
@Tag(name = "Admin – Companies", description = "Administration et modération des entreprises")
public class AdminCompanyController {

    private final CompanyService companyService;

    public AdminCompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @PatchMapping("/{companyId}/status")
    @Operation(summary = "Update the verification status of a company",
            description = "Modifies company verification status to NOT_VERIFIED, VERIFIED, or REJECTED. Restricted to administrator role")
    public ResponseEntity<CompanyResponse> updateVerificationStatus(
            @PathVariable Long companyId,
            @RequestBody CompanyStatusUpdateRequest request,
            Authentication authentication) {

        CompanyResponse response = companyService.updateVerificationStatus(companyId, request, authentication);
        return ResponseEntity.ok(response);
    }
}