package com.gpstl.joyn.candidate.controller;

import com.gpstl.joyn.application.dto.ApplicationErrorResponse;
import com.gpstl.joyn.application.dto.CandidateApplicationsPageResponse;
import com.gpstl.joyn.application.exception.ApplicationException;
import com.gpstl.joyn.application.service.ApplicationService;
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
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * Endpoints for a candidate's own application history.
 */
@RestController
@RequestMapping("/api/v1/candidates")
@CrossOrigin(origins = "*")
@Tag(name = "Candidates", description = "Gestion du compte candidat, profil et historique de candidatures")
@SecurityRequirement(name = "bearerAuth")
public class CandidateApplicationsController {

    private static final Set<String> SUPPORTED_QUERY_PARAMETERS = Set.of("page", "limit", "status", "sort");

    private final ApplicationService applicationService;

    public CandidateApplicationsController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping("/applications")
    @Operation(
            summary = "List the authenticated candidate's applications",
            description = "Returns a paginated application history with job, company, and optional conversation "
                    + "details. The candidate identity is taken exclusively from the JWT subject. "
                    + "Supported statuses are PENDING, VIEWED, MATCHED, IN_REVIEW, INTERVIEW, ACCEPTED, "
                    + "REJECTED, and WITHDRAWN. Sorting supports created_at,asc and created_at,desc.")
    @ApiResponse(
            responseCode = "200",
            description = "Application history returned",
            content = @Content(schema = @Schema(implementation = CandidateApplicationsPageResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Invalid or unsupported query parameter",
            content = @Content(schema = @Schema(implementation = ApplicationErrorResponse.class)))
    @ApiResponse(
            responseCode = "401",
            description = "Authentication is required",
            content = @Content(schema = @Schema(implementation = ApplicationErrorResponse.class)))
    @ApiResponse(
            responseCode = "403",
            description = "An active candidate account is required",
            content = @Content(schema = @Schema(implementation = ApplicationErrorResponse.class)))
    public ResponseEntity<CandidateApplicationsPageResponse> listApplications(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "One-based page number", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Page size, from 1 to 50", example = "20")
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "Optional status filter, such as PENDING or REJECTED")
            @RequestParam(required = false) String status,
            @Parameter(description = "Sort by creation date; defaults to created_at,desc",
                    example = "created_at,desc")
            @RequestParam(defaultValue = "created_at,desc") String sort,
            @Parameter(hidden = true)
            @RequestParam MultiValueMap<String, String> queryParameters) {
        validateQueryParameters(queryParameters);
        Long userId = ApplicationService.userIdFromSubject(jwt.getSubject());
        return ResponseEntity.ok(applicationService.findCandidateHistory(userId, page, limit, status, sort));
    }

    private static void validateQueryParameters(MultiValueMap<String, String> queryParameters) {
        if (!SUPPORTED_QUERY_PARAMETERS.containsAll(queryParameters.keySet())
                || queryParameters.values().stream().anyMatch(values -> values.size() != 1)) {
            throw ApplicationException.badRequest("Unsupported or repeated query parameter");
        }
    }
}
