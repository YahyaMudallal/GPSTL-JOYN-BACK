package com.gpstl.joyn.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * Application history item enriched with the job, company, and conversation details.
 */
@Schema(description = "Candidate application history item")
public record CandidateApplicationResponse(
        @Schema(description = "Application identifier", example = "52")
        Long id,
        @Schema(description = "Job offer identifier", example = "34")
        Long jobOfferId,
        @Schema(description = "Job title", example = "Software Engineer")
        String jobTitle,
        @Schema(description = "Company identifier", example = "12")
        Long companyId,
        @Schema(description = "Company name", example = "Example Company")
        String companyName,
        @Schema(description = "Current application status", example = "PENDING")
        String status,
        @Schema(description = "When the recruiter first viewed the application")
        OffsetDateTime viewedAt,
        @Schema(description = "Message submitted with the application")
        String coverMessage,
        @Schema(description = "Resume URL submitted with the application")
        String resumeUrl,
        @Schema(description = "Application creation date")
        OffsetDateTime createdAt,
        @Schema(description = "Application last update date")
        OffsetDateTime updatedAt,
        @Schema(description = "Conversation identifier, when a conversation exists")
        Long conversationId) {
}
