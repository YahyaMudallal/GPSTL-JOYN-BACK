package com.gpstl.joyn.recruiter.controller;

import com.gpstl.joyn.common.dto.PageResponse;
import com.gpstl.joyn.joboffer.dto.ApiError;
import com.gpstl.joyn.recruiter.dto.AdminRecruiterItem;
import com.gpstl.joyn.recruiter.service.AdminRecruiterListService;
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

@RestController
@RequestMapping("/api/v1/admin/recruiters")
@Tag(name = "Admin – Recruiters", description = "Gestion et modération des recruteurs par l'administrateur")
@SecurityRequirement(name = "bearerAuth")
public class AdminRecruiterListController {

    private final AdminRecruiterListService adminRecruiterListService;

    public AdminRecruiterListController(AdminRecruiterListService adminRecruiterListService) {
        this.adminRecruiterListService = adminRecruiterListService;
    }

    @GetMapping
    @Operation(summary = "Get all recruiters",
            description = "Liste paginée avec recherche textuelle (email, nom, prénom, entreprise), filtres et tri.")
    @ApiResponse(responseCode = "200", description = "Page de recruteurs (data + pagination)")
    @ApiResponse(responseCode = "400", description = "Paramètre invalide (page, limit, status, sort_by, order)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Token absent, expiré ou invalide", content = @Content)
    @ApiResponse(responseCode = "403", description = "L'utilisateur n'est pas administrateur", content = @Content)
    public ResponseEntity<PageResponse<AdminRecruiterItem>> listRecruiters(
            @Parameter(description = "Numéro de page, à partir de 1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Nombre d'éléments par page (1 à 100)")
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "Texte recherché dans l'email, le nom, le prénom ou le nom de l'entreprise")
            @RequestParam(required = false) String search,
            @Parameter(description = "ACTIVE ou SUSPENDED")
            @RequestParam(required = false) String status,
            @Parameter(description = "ID d'une entreprise spécifique")
            @RequestParam(name = "company_id", required = false) Long companyId,
            @Parameter(description = "created_at, email ou lastName")
            @RequestParam(name = "sort_by", defaultValue = "created_at") String sortBy,
            @Parameter(description = "asc ou desc")
            @RequestParam(defaultValue = "desc") String order) {

        return ResponseEntity.ok(adminRecruiterListService.listRecruiters(
                page, limit, search, status, companyId, sortBy, order));
    }
}