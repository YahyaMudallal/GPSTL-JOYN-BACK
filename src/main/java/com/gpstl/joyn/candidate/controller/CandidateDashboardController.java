package com.gpstl.joyn.candidate.controller;

import com.gpstl.joyn.candidate.dto.CandidateDashboardResponse;
import com.gpstl.joyn.candidate.service.CandidateDashboardService;
import com.gpstl.joyn.common.dto.DashboardPeriod;
import com.gpstl.joyn.common.exception.DashboardException;
import com.gpstl.joyn.joboffer.dto.ApiError;
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
 * CA36 – Tableau de bord du candidat connecté.
 *
 * Deux adresses pour la même réponse, car la carte Trello cite les deux :
 *   GET /api/v1/candidates/dashboard
 *   GET /api/v1/candidates/dashboard/stats
 *
 * Sécurité : SecurityConfig exige le rôle CANDIDATE (sans token -> 401, autre rôle -> 403).
 * Pas d'id dans l'URL : le candidat est celui du token (claim "sub"), il ne peut pas viser quelqu'un d'autre.
 */
@RestController
@RequestMapping("/api/v1/candidates/dashboard")
@Tag(name = "Candidates", description = "Gestion du compte candidat, profil et historique de candidatures")
@SecurityRequirement(name = "bearerAuth")
public class CandidateDashboardController {

    private final CandidateDashboardService dashboardService;

    public CandidateDashboardController(CandidateDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping({"", "/stats"})
    @Operation(summary = "Get the authenticated candidate's dashboard",
            description = "Counters for applications (by status), swipes, matches, conversations, "
                    + "upcoming interviews and the 10 most recent activities. "
                    + "The candidate is taken from the JWT subject only.")
    @ApiResponse(responseCode = "200", description = "Dashboard of the candidate")
    @ApiResponse(responseCode = "400", description = "Invalid period (accepted: 7d, 30d, all)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Token missing, invalid or expired", content = @Content)
    @ApiResponse(responseCode = "403", description = "Not a candidate, or account suspended", content = @Content)
    @ApiResponse(responseCode = "404", description = "Candidate profile not found", content = @Content)
    public ResponseEntity<CandidateDashboardResponse> getDashboard(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "7d, 30d or all (default: all)")
            @RequestParam(required = false) String period) {

        DashboardPeriod parsedPeriod = DashboardPeriod.parse(period, DashboardPeriod.ALL);
        return ResponseEntity.ok(dashboardService.getDashboard(userId(jwt), parsedPeriod));
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
