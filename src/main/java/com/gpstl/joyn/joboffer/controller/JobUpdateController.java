package com.gpstl.joyn.joboffer.controller;

import com.gpstl.joyn.joboffer.service.JobUpdateService;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

/** Reçoit le PATCH HTTP et délègue les règles métier au service de modification. */
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
public class JobUpdateController implements JobManagementEndpoint {
    private final JobUpdateService jobs;
    public JobUpdateController(JobUpdateService jobs) { this.jobs = jobs; }

    @PatchMapping("/{jobId}")
    @Operation(summary = "Update details of an existing job offer", 
    description = "Modifies specific fields of an existing job offer such as salary range, description, or remote policy",
    requestBody =
        @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(schema = @Schema(ref = "/openapi/job-update.yaml#/components/schemas/JobPatch"))))
    @ApiResponse(responseCode = "200", description = "Updated job", content =
        @Content(schema = @Schema(ref = "/openapi/job-update.yaml#/components/schemas/Job")))
    public Map<String, Object> patch(@PathVariable long jobId, @RequestBody Map<String, Object> body, Principal principal) {
        long userId = JobRequestIdentity.userId(principal);
        JobRequestIdentity.validateId(jobId);
        return jobs.patch(userId, jobId, body);
    }
}
