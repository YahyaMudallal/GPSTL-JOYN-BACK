package com.gpstl.joyn.company.controller;

import com.gpstl.joyn.common.dto.PageResponse;
import com.gpstl.joyn.company.dto.AdminCompanyItem;
import com.gpstl.joyn.company.service.AdminCompanyListService;
import com.gpstl.joyn.joboffer.dto.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A66 – Liste paginée et filtrée des entreprises pour le back-office.
 * Réservée au rôle ADMIN (règle "/api/v1/admin/**" dans SecurityConfig) :
 * sans token -> 401, token candidat ou recruteur -> 403.
 */
@RestController
@RequestMapping("/api/v1/admin/companies")
@Tag(name = "Admin – Companies", description = "Administration et modération des entreprises")
@SecurityRequirement(name = "bearerAuth")
public class AdminCompanyListController {

    private final AdminCompanyListService adminCompanyListService;

    public AdminCompanyListController(AdminCompanyListService adminCompanyListService) {
        this.adminCompanyListService = adminCompanyListService;
    }

    @GetMapping
    @Operation(summary = "List companies with pagination and filters",
            description = "Returns a paginated list of companies supporting text search, verification status filters, and sorting")
    @ApiResponse(responseCode = "200", description = "Page d'entreprises (data + pagination)")
    @ApiResponse(responseCode = "400", description = "Paramètre invalide (page, limit, status, company_size, sort_by, order)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Token absent, expiré ou invalide", content = @Content)
    @ApiResponse(responseCode = "403", description = "L'utilisateur n'est pas administrateur", content = @Content)
    public ResponseEntity<PageResponse<AdminCompanyItem>> listCompanies(
            @Parameter(description = "Numéro de page, à partir de 1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Nombre d'entreprises par page (1 à 100)")
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "Texte recherché dans le nom, le SIRET ou le domaine email")
            @RequestParam(required = false) String search,
            @Parameter(description = "NOT_VERIFIED, VERIFIED ou REJECTED")
            @RequestParam(required = false) String status,
            @Parameter(description = "Secteur, ex. Fintech")
            @RequestParam(required = false) String sector,
            @Parameter(description = "MICRO, SMALL, MEDIUM ou LARGE")
            @RequestParam(name = "company_size", required = false) String companySize,
            @Parameter(description = "created_at ou name")
            @RequestParam(name = "sort_by", defaultValue = "created_at") String sortBy,
            @Parameter(description = "asc ou desc")
            @RequestParam(defaultValue = "desc") String order) {

        return ResponseEntity.ok(adminCompanyListService.listCompanies(
                page, limit, search, status, sector, companySize, sortBy, order));
    }
}
