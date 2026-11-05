
package com.gpstl.joyn.joboffer.controller;

import com.gpstl.joyn.joboffer.exception.JobApiException;
import com.gpstl.joyn.joboffer.service.JobClosureService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/v1/jobs")
@Tag(name = "Jobs", description = "Cycle de vie des offres d'emploi, de leur publication à leur clôture")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "Invalid request or protected field"),
    @ApiResponse(responseCode = "401", description = "Trusted authentication is missing or invalid"),
    @ApiResponse(responseCode = "403", description = "Role, account status or company does not permit this action"),
    @ApiResponse(responseCode = "404", description = "Job does not exist or is soft deleted"),
    @ApiResponse(responseCode = "500", description = "Internal error; transaction rolled back")
})
public class JobClosureController implements JobManagementEndpoint {

    private final JobClosureService jobs;

    public JobClosureController(JobClosureService jobs) {
        this.jobs = jobs;
    }

    // Existing endpoint: keep unchanged
    @DeleteMapping("/{jobId}")
    @Operation(
        summary = "Soft delete a job while preserving its history",
        description = "Closes and archives a job offer while safely preserving historical matches and conversations"
    )
    @ApiResponse(responseCode = "204", description = "Job closed; no response body")
    public ResponseEntity<Void> close(
            @PathVariable long jobId,
            @RequestBody(required = false) Map<String, Object> body,
            Principal principal) {

        long userId = JobRequestIdentity.userId(principal);
        JobRequestIdentity.validateId(jobId);

        String reason = null;

        if (body != null) {
            if (body.keySet().stream().anyMatch(key -> !key.equals("reason"))) {
                throw new JobApiException(400, "Unknown delete field");
            }

            Object raw = body.get("reason");

            if (raw != null && (!(raw instanceof String) || ((String) raw).length() > 2000)) {
                throw new JobApiException(400, "reason must be a string of at most 2000 characters");
            }

            reason = (String) raw;
        }

        jobs.close(userId, jobId, reason);
        return ResponseEntity.noContent().build();
    }

    // R74: Archive an offer with its recruitment outcome
    @PostMapping("/{jobId}/archive")
    @Operation(summary = "Archive a job with its recruitment outcome")
    @ApiResponse(responseCode = "204", description = "Job archived")
    public ResponseEntity<Void> archive(
            @PathVariable long jobId,
            @RequestBody Map<String, Object> body,
            Principal principal) {

        long userId = JobRequestIdentity.userId(principal);
        JobRequestIdentity.validateId(jobId);

        if (body == null || body.keySet().stream()
                .anyMatch(key -> !key.equals("outcome")
                        && !key.equals("hiredApplicationId"))) {
            throw new JobApiException(400, "Invalid archive request");
        }

        Object rawOutcome = body.get("outcome");

        if (!(rawOutcome instanceof String)) {
            throw new JobApiException(400, "outcome is required");
        }

        Long hiredApplicationId = null;
        Object rawId = body.get("hiredApplicationId");

        if (rawId != null) {
            if (!(rawId instanceof Number number)
                    || number.longValue() <= 0
                    || number.doubleValue() != number.longValue()) {
                throw new JobApiException(400, "Invalid hiredApplicationId");
            }

            hiredApplicationId = number.longValue();
        }

        jobs.archive(userId, jobId, (String) rawOutcome, hiredApplicationId);

        return ResponseEntity.noContent().build();
    }
}
