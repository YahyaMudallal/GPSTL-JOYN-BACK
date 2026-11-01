package com.gpstl.joyn.candidate.controller;

import com.gpstl.joyn.candidate.dto.AdminCandidateFilters;
import com.gpstl.joyn.candidate.dto.AdminCandidateItem;
import com.gpstl.joyn.candidate.service.AdminCandidateListService;
import com.gpstl.joyn.common.dto.PageResponse;
import com.gpstl.joyn.joboffer.dto.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.LocalDate;

/**
 * A80 – Liste paginée et filtrée des candidats pour le back-office.
 * Réservée au rôle ADMIN (règle "/api/v1/admin/**" dans SecurityConfig) :
 * sans token -> 401, token candidat ou recruteur -> 403.
 */
@RestController
@RequestMapping("/api/v1/admin/candidates")
@Tag(name = "Admin – Candidats", description = "Gestion des candidats par l'administrateur")
@SecurityRequirement(name = "bearerAuth")
public class AdminCandidateListController {

    private final AdminCandidateListService adminCandidateListService;

    public AdminCandidateListController(AdminCandidateListService adminCandidateListService) {
        this.adminCandidateListService = adminCandidateListService;
    }

    @GetMapping
    @Operation(summary = "Lister les candidats",
            description = "Liste paginée, avec recherche (email, prénom, nom), filtres sur le compte et le profil, et tri.")
    @ApiResponse(responseCode = "200", description = "Page de candidats (data + pagination)")
    @ApiResponse(responseCode = "400", description = "Paramètre invalide (pagination, filtre, sort_by ou order)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Token absent, expiré ou invalide", content = @Content)
    @ApiResponse(responseCode = "403", description = "L'utilisateur n'est pas un administrateur actif", content = @Content)
    @ApiResponse(responseCode = "500", description = "Erreur interne", content = @Content)
    public PageResponse<AdminCandidateItem> listCandidates(
            @Parameter(description = "Numéro de page, à partir de 1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Nombre de candidats par page (1 à 100)")
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "Texte recherché dans l'email, le prénom ou le nom")
            @RequestParam(required = false) String search,
            @Parameter(description = "ACTIVE ou SUSPENDED")
            @RequestParam(required = false) String status,
            @Parameter(description = "true : email vérifié ; false : email non vérifié")
            @RequestParam(name = "email_verified", required = false) Boolean emailVerified,
            @Parameter(description = "Inscrit à partir de cette date incluse, AAAA-MM-JJ (UTC)")
            @RequestParam(name = "created_from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @Parameter(description = "Inscrit jusqu'à cette date incluse, AAAA-MM-JJ (UTC)")
            @RequestParam(name = "created_to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
            @Parameter(description = "Dernière connexion avant cette date, AAAA-MM-JJ (UTC) ; exclut les comptes jamais connectés")
            @RequestParam(name = "last_login_before", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate lastLoginBefore,
            @Parameter(description = "Texte recherché dans la localisation, ex. Paris")
            @RequestParam(required = false) String location,
            @Parameter(description = "true : profil visible par les recruteurs ; false : profil masqué")
            @RequestParam(name = "visible_to_recruiters", required = false) Boolean visibleToRecruiters,
            @Parameter(description = "true : CV déposé ; false : aucun CV")
            @RequestParam(name = "has_resume", required = false) Boolean hasResume,
            @Parameter(description = "created_at, email, first_name ou last_name")
            @RequestParam(name = "sort_by", defaultValue = "created_at") String sortBy,
            @Parameter(description = "asc ou desc")
            @RequestParam(defaultValue = "desc") String order,
            @Parameter(hidden = true) Principal principal) {

        AdminCandidateFilters filters = new AdminCandidateFilters(search, status, emailVerified,
                createdFrom, createdTo, lastLoginBefore, location, visibleToRecruiters, hasResume);
        return adminCandidateListService.listCandidates(userId(principal), page, limit, filters, sortBy, order);
    }

    /** users.id vient du claim « sub » du token vérifié. */
    private static long userId(Principal principal) {
        try {
            return Long.parseLong(principal.getName());
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication must provide the database user ID");
        }
    }
}
