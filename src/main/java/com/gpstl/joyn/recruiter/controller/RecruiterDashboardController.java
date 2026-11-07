package com.gpstl.joyn.recruiter.controller;

import com.gpstl.joyn.common.dto.DashboardPeriod;
import com.gpstl.joyn.common.exception.DashboardException;
import com.gpstl.joyn.joboffer.dto.ApiError;
import com.gpstl.joyn.recruiter.dto.RecruiterDashboardResponse;
import com.gpstl.joyn.recruiter.service.RecruiterDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * R49 – Tableau de bord du recruteur connecté.
 *
 * Deux adresses pour la même réponse (la carte Trello cite les deux) :
 *   GET /api/v1/recruiters/dashboard          <- celle qu'appelle déjà le front Flutter
 *   GET /api/v1/recruiters/dashboard/stats
 *
 * Sécurité : la règle "/api/v1/recruiters/**" de SecurityConfig exige déjà le rôle RECRUITER
 * (sans token -> 401, autre rôle -> 403). Rien à modifier dans SecurityConfig.
 */
@RestController
@RequestMapping("/api/v1/recruiters/dashboard")
@Tag(name = "Recruiters", description = "Permet aux recruteurs de s'enregistrer et de gérer les informations de leur profil")
@SecurityRequirement(name = "bearerAuth")
public class RecruiterDashboardController {

    private final RecruiterDashboardService dashboardService;

    public RecruiterDashboardController(RecruiterDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping({"", "/stats"})
    @Operation(summary = "Get the authenticated recruiter's dashboard",
            description = "KPIs, trend, status distribution, per-offer breakdown, recent activity and priority "
                    + "actions for the recruiter's company. Optional job_id narrows everything to one offer "
                    + "of the same company.")
    @ApiResponse(responseCode = "200", description = "Dashboard of the recruiter's company")
    @ApiResponse(responseCode = "400", description = "Invalid period (7d, 30d, all) or job_id",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Token missing, invalid or expired", content = @Content)
    @ApiResponse(responseCode = "403", description = "Not a recruiter, account suspended, or offer of another company",
            content = @Content)
    @ApiResponse(responseCode = "404", description = "Recruiter profile or job offer not found", content = @Content)
    public ResponseEntity<RecruiterDashboardResponse> getDashboard(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "7d, 30d or all (default: 30d)")
            @RequestParam(required = false) String period,
            @Parameter(description = "Optional: id of one job offer of the company")
            @RequestParam(name = "job_id", required = false) Long jobId) {

        DashboardPeriod parsedPeriod = DashboardPeriod.parse(period, DashboardPeriod.THIRTY_DAYS);
        if (jobId != null && jobId <= 0) {
            throw DashboardException.badRequest("job_id", "job_id doit être un entier positif");
        }
        return ResponseEntity.ok(dashboardService.getDashboard(userId(jwt), parsedPeriod, jobId));
    }

    /** users.id vient uniquement du claim "sub" du token vérifié. */
    private static long userId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw DashboardException.unauthorized("Authentication is required");
        }
        try {
            long id = Long.parseLong(jwt.getSubject());
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException ex) {
            throw DashboardException.unauthorized("Invalid authenticated user identity");
        }
    }
}
