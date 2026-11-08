package com.gpstl.joyn.candidate.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gpstl.joyn.candidate.model.CandidateRecommendationScore;
import com.gpstl.joyn.joboffer.dto.JobOfferResponse;
import com.gpstl.joyn.joboffer.model.JobOffer;

public record CandidateRecommendationItem(
        JobOfferResponse offer,
        @JsonProperty("match_score") int matchScore,
        @JsonProperty("score_breakdown") ScoreBreakdown scoreBreakdown) {

    public static CandidateRecommendationItem from(
        JobOffer offer, 
        CandidateRecommendationScore score) {
    return new CandidateRecommendationItem(
        JobOfferResponse.from(offer),
        score.getMatchScore().intValue(),
        new ScoreBreakdown(
            toInt(score.getSkillScore()),
            toInt(score.getExperienceScore()),
            toInt(score.getEducationScore()),
            toInt(score.getLocationScore()),
            toInt(score.getAvailabilityScore()),
            score.getDistanceKm() == null ? null : score.getDistanceKm().doubleValue()));
    }

    private static Integer toInt(Short value) {
    return value == null ? null : value.intValue();
    }

    public record ScoreBreakdown(
        Integer skills,
        Integer experience,
        Integer education,
        Integer location,
        Integer availability,
        @JsonProperty("distance_km") Double distanceKm) {
    }
}
