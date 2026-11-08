package com.gpstl.joyn.joboffer.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Paginated applicants for a recruiter-owned job offer, ranked by stored match score")
public record RecruiterJobApplicantsPageResponse(
        List<RecruiterJobApplicantItem> content,
        int page,
        int limit,
        long totalElements,
        int totalPages,
        boolean hasNext) {
}
