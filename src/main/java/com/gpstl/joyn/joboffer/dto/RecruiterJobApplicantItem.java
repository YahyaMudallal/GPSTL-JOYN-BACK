package com.gpstl.joyn.joboffer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gpstl.joyn.candidate.dto.CandidateProfileResponse;

import java.math.BigDecimal;
import java.time.Instant;

public record RecruiterJobApplicantItem(
        @JsonProperty("application_id") Long applicationId,
        @JsonProperty("application_status") String applicationStatus,
        @JsonProperty("applied_at") Instant appliedAt,
        @JsonProperty("match_score") Integer matchScore,
        @JsonProperty("score_breakdown") ScoreBreakdown scoreBreakdown,
        CandidateProfileResponse candidate) {

    public record ScoreBreakdown(
            Integer skills,
            Integer experience,
            Integer education,
            Integer location,
            Integer availability,
            @JsonProperty("distance_km") BigDecimal distanceKm) {
    }
}
