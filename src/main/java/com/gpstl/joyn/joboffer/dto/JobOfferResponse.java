package com.gpstl.joyn.joboffer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gpstl.joyn.common.model.Skill;
import com.gpstl.joyn.joboffer.model.JobOffer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Offre renvoyée par l'API (réponse 201 de la création).
 */
public record JobOfferResponse(
        Long id,
        @JsonProperty("company_id") Long companyId,
        @JsonProperty("recruiter_id") Long recruiterId,
        String title,
        @JsonProperty("contract_type") String contractType,
        String location,
        @JsonProperty("remote_policy") String remotePolicy,
        @JsonProperty("experience_level") String experienceLevel,
        String description,
        @JsonProperty("salary_min") BigDecimal salaryMin,
        @JsonProperty("salary_max") BigDecimal salaryMax,
        String currency,
        @JsonProperty("salary_period") String salaryPeriod,
        List<String> skills,
        @JsonProperty("rhythm_type") String rhythmType,
        @JsonProperty("start_date") LocalDate startDate,
        @JsonProperty("duration_months") Integer durationMonths,
        @JsonProperty("degree_level_required") String degreeLevelRequired,
        String status,
        @JsonProperty("published_at") OffsetDateTime publishedAt,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("updated_at") OffsetDateTime updatedAt
) {

    public static JobOfferResponse from(JobOffer offer) {
        return new JobOfferResponse(
                offer.getId(),
                offer.getCompany().getId(),
                offer.getRecruiter().getId(),
                offer.getTitle(),
                offer.getContractType().getCode(),
                offer.getLocation(),
                offer.getRemoteMode().name(),
                offer.getExperienceLevelRequired() == null ? null : offer.getExperienceLevelRequired().name(),
                offer.getDescription(),
                offer.getSalaryMin(),
                offer.getSalaryMax(),
                offer.getSalaryCurrency(),
                offer.getSalaryPeriod().name(),
                offer.getSkills().stream().map(Skill::getName).toList(),
                offer.getRhythmType(),
                offer.getStartDate(),
                offer.getDurationMonths() == null ? null : offer.getDurationMonths().intValue(),
                offer.getDegreeLevelRequired(),
                offer.getStatus().name(),
                offer.getPublishedAt(),
                offer.getCreatedAt(),
                offer.getUpdatedAt());
    }
}
