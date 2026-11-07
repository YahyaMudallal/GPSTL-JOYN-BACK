package com.gpstl.joyn.joboffer.repository;

import java.math.BigDecimal;
import java.time.Instant;

public interface RecruiterJobApplicantProjection {
    Long getApplicationId();
    Long getCandidateId();
    String getApplicationStatus();
    Instant getAppliedAt();
    Short getMatchScore();
    Short getSkillScore();
    Short getExperienceScore();
    Short getEducationScore();
    Short getLocationScore();
    Short getAvailabilityScore();
    BigDecimal getDistanceKm();
}
