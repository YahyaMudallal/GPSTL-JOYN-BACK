package com.gpstl.joyn.joboffer.controller;

import com.gpstl.joyn.joboffer.dto.RecruiterJobApplicantsPageResponse;
import com.gpstl.joyn.joboffer.exception.JobApiException;
import com.gpstl.joyn.joboffer.service.RecruiterJobApplicantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/recruiters/jobs")
@Tag(name = "Offres (recruteur)")
@SecurityRequirement(name = "bearerAuth")
public class RecruiterJobApplicantController implements JobManagementEndpoint {

    private static final Set<String> SUPPORTED_QUERY_PARAMETERS = Set.of(
            "page",
            "limit");

    private final RecruiterJobApplicantService applicantService;

    public RecruiterJobApplicantController(RecruiterJobApplicantService applicantService) {
        this.applicantService = applicantService;
    }

    @GetMapping("/{jobId}/recommendations")
    @Operation(
            summary = "List applicants for a job ranked by their stored matching score",
            description = "Returns only candidates who applied to this offer. The score and its breakdown are "
                    + "read from the recommendation score already stored for this candidate and offer; "
                    + "candidates without a stored score appear last.")
    public ResponseEntity<RecruiterJobApplicantsPageResponse> listApplicants(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long jobId,
            @Parameter(description = "One-based page number", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Number of applicants per page, from 1 to 50", example = "20")
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> queryParameters) {
        validateQueryParameters(queryParameters);
        return ResponseEntity.ok(applicantService.findApplicants(
                userId(jwt.getSubject()),
                jobId,
                page,
                limit));
    }

    private static void validateQueryParameters(MultiValueMap<String, String> queryParameters) {
        if (!SUPPORTED_QUERY_PARAMETERS.containsAll(queryParameters.keySet())
                || queryParameters.entrySet().stream()
                .anyMatch(entry -> entry.getValue().size() > 1)) {
            throw new JobApiException(400, "Unsupported or repeated query parameter");
        }
    }

    private static long userId(String subject) {
        try {
            long id = Long.parseLong(subject);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException | NullPointerException exception) {
            throw new JobApiException(401, "Invalid authenticated user identity");
        }
    }
}
