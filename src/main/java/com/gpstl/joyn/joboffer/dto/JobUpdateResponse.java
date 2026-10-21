package com.gpstl.joyn.joboffer.dto;

import com.gpstl.joyn.common.model.Skill;
import com.gpstl.joyn.joboffer.model.JobOffer;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Convertit l’entité JPA au format JSON snake_case existant, sans exposer directement ses relations. */
public final class JobUpdateResponse {
    private JobUpdateResponse() {}

    public static Map<String, Object> from(JobOffer job) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", job.getId());
        result.put("company_id", job.getCompany().getId());
        result.put("recruiter_id", job.getRecruiter().getId());
        result.put("contract_type_id", job.getContractType().getId());
        result.put("title", job.getTitle());
        result.put("description", job.getDescription());
        result.put("location", job.getLocation());
        result.put("salary_min", job.getSalaryMin());
        result.put("salary_max", job.getSalaryMax());
        result.put("salary_currency", job.getSalaryCurrency());
        result.put("salary_period", job.getSalaryPeriod().name());
        result.put("remote_mode", job.getRemoteMode().name());
        result.put("status", job.getStatus().name());
        result.put("rhythm_type", job.getRhythmType());
        result.put("start_date", job.getStartDate() == null ? null : job.getStartDate().toString());
        result.put("duration_months", job.getDurationMonths());
        result.put("degree_level_required", job.getDegreeLevelRequired());
        result.put("experience_level_required", job.getExperienceLevelRequired() == null ? null : job.getExperienceLevelRequired().name());
        result.put("published_at", timestamp(job.getPublishedAt()));
        result.put("expires_at", timestamp(job.getExpiresAt()));
        result.put("deleted_at", timestamp(job.getDeletedAt()));
        result.put("created_at", timestamp(job.getCreatedAt()));
        result.put("updated_at", timestamp(job.getUpdatedAt()));
        result.put("skills", job.getSkills().stream().map(Skill::getName).sorted().toList());
        return result;
    }

    private static String timestamp(OffsetDateTime value) {
        return value == null ? null : value.toInstant().toString();
    }
}
