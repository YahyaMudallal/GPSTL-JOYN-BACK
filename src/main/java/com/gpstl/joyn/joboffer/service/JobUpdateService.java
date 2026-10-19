package com.gpstl.joyn.joboffer.service;

import com.gpstl.joyn.joboffer.repositories.JobAccessRepository;
import com.gpstl.joyn.joboffer.repositories.JobUpdateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import com.gpstl.joyn.joboffer.dto.JobPatch;
import com.gpstl.joyn.joboffer.exception.JobApiException;
import java.math.BigDecimal;

@Service
public class JobUpdateService {
    private final JobUpdateRepository jobs;
    private final JobAccessService access;
    private final JobAccessRepository audit;
    public JobUpdateService(JobUpdateRepository jobs, JobAccessService access, JobAccessRepository audit) {
        this.jobs = jobs; this.access = access; this.audit = audit;
    }

    @Transactional
    public Map<String, Object> patch(long userId, long jobId, Map<String, Object> body) {
        Map<String, Object> job = access.authorizedJob(userId, jobId);
        JobPatch patch = JobPatch.parse(body);
        Map<String, Object> columns = patch.columns();
        Object min = columns.containsKey("salary_min") ? columns.get("salary_min") : job.get("salary_min");
        Object max = columns.containsKey("salary_max") ? columns.get("salary_max") : job.get("salary_max");
        if (min != null && max != null && new BigDecimal(min.toString()).compareTo(new BigDecimal(max.toString())) > 0)
            throw new JobApiException(400, "Invalid salary range", Map.of("salary_max", "Must be greater than or equal to salary_min"));
        if (columns.containsKey("contract_type_id") && !jobs.contractExists((Integer) columns.get("contract_type_id")))
            throw new JobApiException(400, "Invalid contract type", Map.of("contract_type_id", "Unknown contract type"));
        jobs.update(jobId, columns);
        if (patch.skills() != null) jobs.replaceSkills(jobId, patch.skills());
        audit.audit(userId, jobId, "UPDATE", null);
        return jobs.response(jobId);
    }
}
