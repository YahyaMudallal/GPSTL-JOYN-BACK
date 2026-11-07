package com.gpstl.joyn.candidate.controller;

import com.gpstl.joyn.application.exception.ApplicationException;
import com.gpstl.joyn.application.service.ApplicationService;
import com.gpstl.joyn.candidate.dto.CandidateRecommendationPageResponse;
import com.gpstl.joyn.candidate.service.CandidateRecommendationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/candidates")
@Tag(name = "Candidates")
@SecurityRequirement(name = "bearerAuth")
public class CandidateRecommendationController {

    private static final Set<String> SUPPORTED_QUERY_PARAMETERS = Set.of(
            "page",
            "limit", 
            "contract_type", 
            "rhythm_type", 
            "remote", 
            "location",
            "max_distance_km");

    private final CandidateRecommendationService recommendationService;

    public CandidateRecommendationController(CandidateRecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/recommendations")
    @Operation(
            summary = "List personalized job recommendations",
            description = "Returns active, unexpired offers not previously swiped or applied to, sorted by "
                    + "weighted matching score (skills 40%, experience 25%, education 15%, location 12%, "
                    + "availability 8%; unspecified criteria are omitted and the remaining weights normalized). "
                    + "Scores are persisted for later recruiter access. "
                    + "distance_km is the Haversine distance; lower is closer. Location affinity decreases as "
                    + "the distance grows. max_distance_km filters out non-remote offers without coordinates.")
    public ResponseEntity<CandidateRecommendationPageResponse> listRecommendations(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(
                description = "One-based page number", 
                example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(
                description = "Number of results per page, from 1 to 50", 
                example = "20")
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "Contract code from contract_types, for example CDI or INTERNSHIP")
            @RequestParam(
                name = "contract_type", 
                required = false) String contractType,
            @Parameter(description = "Alternance rhythm, for example 3/2")
            @RequestParam(
                name = "rhythm_type", 
                required = false) String rhythmType,
            @Parameter(description = "Work mode: ONSITE, HYBRID, or REMOTE")
            @RequestParam(required = false) String remote,
            @Parameter(description = "Location text contained in the offer location")
            @RequestParam(required = false) String location,
            @Parameter(
                description = "Maximum distance in kilometers; remote offers are not distance-limited", example = "50")
            @RequestParam(name = "max_distance_km", required = false) Double maxDistanceKm,
            @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> queryParameters) {
        validateQueryParameters(queryParameters);
        Long userId = ApplicationService.userIdFromSubject(jwt.getSubject());
        return ResponseEntity.ok(recommendationService.findRecommendations(
                userId, 
                page, 
                limit, 
                contractType, 
                rhythmType, 
                remote, 
                location,
                maxDistanceKm));
    }

    private static void validateQueryParameters(MultiValueMap<String, String> queryParameters) {
        if (!SUPPORTED_QUERY_PARAMETERS.containsAll(queryParameters.keySet())
                || queryParameters.values().stream().anyMatch(values -> values.size() != 1)) {
            throw ApplicationException.badRequest("Unsupported or repeated query parameter");
        }
    }
}
