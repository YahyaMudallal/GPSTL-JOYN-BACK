package com.gpstl.joyn.application.controller;

import com.gpstl.joyn.application.dto.ApplicationResponse;
import com.gpstl.joyn.application.dto.ApplicationErrorResponse;
import com.gpstl.joyn.application.dto.CreateApplicationRequest;
import com.gpstl.joyn.application.dto.UpdateApplicationStatusRequest;
import com.gpstl.joyn.application.service.ApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/applications")
@Tag(name = "Applications", description = "Application creation and status management")
@SecurityRequirement(name = "bearerAuth")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    @Operation(
        summary = "Create an application",
        description = "Creates an application for the candidate identified by the JWT, "
                + "with the initial PENDING status.")
    @ApiResponse(
        responseCode = "201",
        description = "Application created",
        content = @Content(schema = @Schema(implementation = ApplicationResponse.class)))
    @ApiResponse(
        responseCode = "400",
        description = "Request validation failed",
        content = @Content(schema = @Schema(implementation = ApplicationErrorResponse.class)))
    @ApiResponse(
        responseCode = "404",
        description = "Job offer not found or unavailable",
        content = @Content(schema = @Schema(implementation = ApplicationErrorResponse.class)))
    @ApiResponse(
        responseCode = "409",
        description = "Candidate already applied to this job offer",
        content = @Content(schema = @Schema(implementation = ApplicationErrorResponse.class)))
    @ApiResponse(
        responseCode = "401",
        description = "Authentication is required",
        content = @Content(schema = @Schema(implementation = ApplicationErrorResponse.class)))
    @ApiResponse(
        responseCode = "403",
        description = "An active candidate account is required",
        content = @Content(schema = @Schema(implementation = ApplicationErrorResponse.class)))
    public ResponseEntity<ApplicationResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateApplicationRequest request) {
        Long userId = ApplicationService.userIdFromSubject(jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED).body(applicationService.create(userId, request));
    }

    @PatchMapping("/{applicationId}/status")
    @Operation(
        summary = "Update an application's status",
        description = "Updates the status using a code from application_statuses.")
    @ApiResponse(
        responseCode = "200",
        description = "Application status updated",
        content = @Content(schema = @Schema(implementation = ApplicationResponse.class)))
    @ApiResponse(
        responseCode = "400",
        description = "Invalid status code",
        content = @Content(schema = @Schema(implementation = ApplicationErrorResponse.class)))
    @ApiResponse(
        responseCode = "404",
        description = "Application not found",
        content = @Content(schema = @Schema(implementation = ApplicationErrorResponse.class)))
    @ApiResponse(
        responseCode = "401",
        description = "Authentication is required",
        content = @Content(schema = @Schema(implementation = ApplicationErrorResponse.class)))
    @ApiResponse(
        responseCode = "403",
        description = "Recruiter does not own the job or is not an administrator",
        content = @Content(schema = @Schema(implementation = ApplicationErrorResponse.class)))
    public ResponseEntity<ApplicationResponse> updateStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long applicationId,
            @Valid @RequestBody UpdateApplicationStatusRequest request) {
        Long userId = ApplicationService.userIdFromSubject(jwt.getSubject());
        return ResponseEntity.ok(applicationService.updateStatus(userId, applicationId, request));
    }
}
