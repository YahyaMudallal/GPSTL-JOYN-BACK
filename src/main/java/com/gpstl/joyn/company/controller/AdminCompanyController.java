package com.gpstl.joyn.company.controller;

import com.gpstl.joyn.company.dto.CompanyResponse;
import com.gpstl.joyn.company.dto.CompanyStatusUpdateRequest;
import com.gpstl.joyn.company.service.CompanyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/companies")
public class AdminCompanyController {

    private final CompanyService companyService;

    public AdminCompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @PatchMapping("/{companyId}/status")
    public ResponseEntity<CompanyResponse> updateVerificationStatus(
            @PathVariable Long companyId,
            @RequestBody CompanyStatusUpdateRequest request,
            Authentication authentication) {

        CompanyResponse response = companyService.updateVerificationStatus(companyId, request, authentication);
        return ResponseEntity.ok(response);
    }
}