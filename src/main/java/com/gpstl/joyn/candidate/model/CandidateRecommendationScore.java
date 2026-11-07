package com.gpstl.joyn.candidate.model;

import com.gpstl.joyn.joboffer.model.JobOffer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.math.BigDecimal;

@Entity
@Table(
    name = "candidate_recommendation_scores", 
    uniqueConstraints = @UniqueConstraint(
        name = "uq_candidate_recommendation_score",
        columnNames = {"candidate_id", "job_offer_id"}))
public class CandidateRecommendationScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private Candidate candidate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_offer_id", nullable = false)
    private JobOffer jobOffer;

    @Column(name = "match_score", nullable = false)
    private Short matchScore;

    @Column(name = "skill_score")
    private Short skillScore;

    @Column(name = "experience_score")
    private Short experienceScore;

    @Column(name = "education_score")
    private Short educationScore;

    @Column(name = "location_score")
    private Short locationScore;

    @Column(name = "availability_score")
    private Short availabilityScore;

    @Column(name = "distance_km", precision = 8, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "calculated_at", nullable = false)
    private OffsetDateTime calculatedAt = OffsetDateTime.now(ZoneOffset.UTC);

    protected CandidateRecommendationScore() {
    }

    public CandidateRecommendationScore(Candidate candidate, JobOffer jobOffer, Breakdown breakdown) {
        this.candidate = candidate;
        this.jobOffer = jobOffer;
        update(breakdown);
    }

    public void update(Breakdown breakdown) {
        matchScore = toShort(breakdown.matchScore());
        skillScore = toShort(breakdown.skillScore());
        experienceScore = toShort(breakdown.experienceScore());
        educationScore = toShort(breakdown.educationScore());
        locationScore = toShort(breakdown.locationScore());
        availabilityScore = toShort(breakdown.availabilityScore());
        distanceKm = breakdown.distanceKm() == null
                ? null
                : BigDecimal.valueOf(breakdown.distanceKm()).setScale(2, java.math.RoundingMode.HALF_UP);
        calculatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() { return id; }
    public Candidate getCandidate() { return candidate; }
    public JobOffer getJobOffer() { return jobOffer; }
    public Short getMatchScore() { return matchScore; }
    public Short getSkillScore() { return skillScore; }
    public Short getExperienceScore() { return experienceScore; }
    public Short getEducationScore() { return educationScore; }
    public Short getLocationScore() { return locationScore; }
    public Short getAvailabilityScore() { return availabilityScore; }
    public BigDecimal getDistanceKm() { return distanceKm; }
    public OffsetDateTime getCalculatedAt() { return calculatedAt; }

    private static Short toShort(Integer value) {
        return value == null ? null : value.shortValue();
    }

    public record Breakdown(
        int matchScore,
        Integer skillScore,
        Integer experienceScore,
        Integer educationScore,
        Integer locationScore,
        Integer availabilityScore,
        Double distanceKm) {}
}
