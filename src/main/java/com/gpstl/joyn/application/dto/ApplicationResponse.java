package com.gpstl.joyn.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "Created or updated application")
public record ApplicationResponse(
    @Schema(example = "52")
    Long id,
    @Schema(example = "12")
    Long candidateId,
    @Schema(example = "34")
    Long jobOfferId,
    @Schema(example = "IN_REVIEW")
    String status,
    @Schema(example = "2026-10-24T14:03:10Z")
    OffsetDateTime viewedAt,
    @Schema(example = "I am excited to apply for this position.")
    String coverMessage,
    @Schema(example = "https://example.com/resumes/candidate-12.pdf")
    String resumeUrl,
    @Schema(example = "2026-10-24T14:03:10Z")
    OffsetDateTime createdAt,
    @Schema(example = "2026-10-24T14:03:10Z")
    OffsetDateTime updatedAt
) {}
