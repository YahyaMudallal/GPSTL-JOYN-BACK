package com.gpstl.joyn.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * A page of candidate-owned applications and pagination metadata.
 */
@Schema(description = "Paginated candidate application history")
public record CandidateApplicationsPageResponse(
        @Schema(description = "Applications on the requested page")
        List<CandidateApplicationResponse> content,
        @Schema(description = "One-based page number", example = "1")
        int page,
        @Schema(description = "Maximum number of applications on a page", example = "20")
        int limit,
        @Schema(description = "Total number of matching applications", example = "42")
        long totalElements,
        @Schema(description = "Total number of matching pages", example = "3")
        int totalPages,
        @Schema(description = "Whether another page is available", example = "true")
        boolean hasNext) {
}
