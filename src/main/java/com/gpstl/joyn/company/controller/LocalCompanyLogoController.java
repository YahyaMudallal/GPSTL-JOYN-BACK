package com.gpstl.joyn.company.controller;

import com.gpstl.joyn.company.service.CompanyLogoStorage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Sert les logos stockés en local (développement sans Supabase, et tests).
 * Même principe que LocalUserProfilePictureController (U86). Public : un logo s'affiche partout.
 */
@RestController
@RequestMapping("/api/v1/companies/logos")
@ConditionalOnProperty(name = "joyn.storage.provider", havingValue = "local")
@Tag(name = "Company Logos", description = "Distribution locale des logos d'entreprise pour le développement")
public class LocalCompanyLogoController {

    private final CompanyLogoStorage storage;

    public LocalCompanyLogoController(CompanyLogoStorage storage) {
        this.storage = storage;
    }

    @GetMapping("/{companyId}/{fileName}")
    @Operation(summary = "Récupérer un logo d'entreprise en mode local")
    public ResponseEntity<Resource> getLogo(@PathVariable("companyId") Long companyId,
                                            @PathVariable("fileName") String fileName) {
        String key = "company_logos/" + companyId + "/" + fileName;
        CompanyLogoStorage.StoredCompanyLogo logo = storage.load(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Logo introuvable"));
        return ResponseEntity.ok()
                .contentType(logo.mediaType())
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(logo.resource());
    }
}
