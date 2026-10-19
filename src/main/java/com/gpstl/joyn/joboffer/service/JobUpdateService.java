package com.gpstl.joyn.joboffer.service;

import com.gpstl.joyn.common.models.Skill;
import com.gpstl.joyn.common.repositories.SkillRepository;
import com.gpstl.joyn.joboffer.dto.JobPatch;
import com.gpstl.joyn.joboffer.dto.JobUpdateResponse;
import com.gpstl.joyn.joboffer.exception.JobApiException;
import com.gpstl.joyn.joboffer.models.*;
import com.gpstl.joyn.joboffer.repositories.ContractTypeRepository;
import com.gpstl.joyn.joboffer.repositories.JobOfferRepository;
import com.gpstl.joyn.joboffer.repositories.JobOfferAuditRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class JobUpdateService {
    private final JobOfferRepository offers;
    private final ContractTypeRepository contracts;
    private final SkillRepository skills;
    private final JobAccessService access;
    private final JobOfferAuditRepository audit;
    private final EntityManager entityManager;

    public JobUpdateService(JobOfferRepository offers, ContractTypeRepository contracts,
            SkillRepository skills, JobAccessService access, JobOfferAuditRepository audit,
            EntityManager entityManager) {
        this.offers = offers;
        this.contracts = contracts;
        this.skills = skills;
        this.access = access;
        this.audit = audit;
        this.entityManager = entityManager;
    }

    @Transactional
    public Map<String, Object> patch(long userId, long jobId, Map<String, Object> body) {
        JobOffer job = access.authorizedJob(userId, jobId);
        JobPatch patch = JobPatch.parse(body);
        Map<String, Object> columns = patch.columns();
        BigDecimal min = columns.containsKey("salary_min") ? (BigDecimal) columns.get("salary_min") : job.getSalaryMin();
        BigDecimal max = columns.containsKey("salary_max") ? (BigDecimal) columns.get("salary_max") : job.getSalaryMax();
        if (min != null && max != null && min.compareTo(max) > 0)
            throw new JobApiException(400, "Invalid salary range", Map.of("salary_max", "Must be greater than or equal to salary_min"));

        ContractType contract = columns.containsKey("contract_type_id")
            ? contracts.findById(((Integer) columns.get("contract_type_id")).shortValue())
                .orElseThrow(() -> new JobApiException(400, "Invalid contract type", Map.of("contract_type_id", "Unknown contract type")))
            : job.getContractType();
        apply(job, columns, contract);
        if (patch.skills() != null) replaceSkills(job, patch.skills());
        job.touch();
        offers.saveAndFlush(job);
        audit.saveAndFlush(new JobOfferAudit(userId, job, "UPDATE", null));
        // PostgreSQL's trigger owns updated_at; reload the database value for the response.
        entityManager.refresh(job);
        return JobUpdateResponse.from(job);
    }

    private void apply(JobOffer job, Map<String, Object> columns, ContractType contract) {
        columns.forEach((field, value) -> {
            switch (field) {
                case "title" -> job.setTitle((String) value);
                case "description" -> job.setDescription((String) value);
                case "location" -> job.setLocation((String) value);
                case "contract_type_id" -> job.setContractType(contract);
                case "salary_min" -> job.setSalaryMin((BigDecimal) value);
                case "salary_max" -> job.setSalaryMax((BigDecimal) value);
                case "salary_currency" -> job.setSalaryCurrency((String) value);
                case "salary_period" -> job.setSalaryPeriod(SalaryPeriod.valueOf((String) value));
                case "remote_mode" -> job.setRemoteMode(RemoteMode.valueOf((String) value));
                case "status" -> job.setStatus(OfferStatus.valueOf((String) value));
                case "rhythm_type" -> job.setRhythmType((String) value);
                case "start_date" -> job.setStartDate((LocalDate) value);
                case "duration_months" -> job.setDurationMonths(value == null ? null : ((Integer) value).shortValue());
                case "degree_level_required" -> job.setDegreeLevelRequired((String) value);
                case "experience_level_required" -> job.setExperienceLevelRequired(value == null ? null : ExperienceLevel.valueOf((String) value));
                default -> throw new IllegalStateException("Unvalidated patch field: " + field);
            }
        });
    }

    private void replaceSkills(JobOffer job, List<String> names) {
        Set<Skill> desired = new LinkedHashSet<>();
        for (String name : names.stream().sorted().toList()) {
            desired.add(skills.findByNameIgnoreCase(name).orElseGet(() -> skills.saveAndFlush(new Skill(name))));
        }
        Set<Long> ids = new HashSet<>();
        desired.forEach(skill -> ids.add(skill.getId()));
        // Mutate the managed Set in place: retained join rows keep their required_level.
        job.getSkills().removeIf(skill -> !ids.contains(skill.getId()));
        job.getSkills().addAll(desired);
    }
}
