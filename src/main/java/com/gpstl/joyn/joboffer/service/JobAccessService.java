package com.gpstl.joyn.joboffer.service;

import com.gpstl.joyn.joboffer.exception.JobApiException;
import com.gpstl.joyn.joboffer.repositories.JobAccessRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.util.Map;

@Service
public class JobAccessService {
    private final JobAccessRepository jobs;
    public JobAccessService(JobAccessRepository jobs) { this.jobs = jobs; }

    // The caller owns the transaction so the row lock remains held through the mutation.
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<String, Object> authorizedJob(long userId, long jobId) {
        Map<String, Object> actor = jobs.actor(userId)
            .orElseThrow(() -> new JobApiException(401, "Authenticated user does not exist"));
        if (!"ACTIVE".equals(actor.get("status"))) throw new JobApiException(403, "Account is suspended");
        String role = (String) actor.get("role");
        if (!"ADMIN".equals(role) && !"RECRUITER".equals(role)) throw new JobApiException(403, "Insufficient permissions");
        Map<String, Object> job = jobs.lockActive(jobId)
            .orElseThrow(() -> new JobApiException(404, "Job not found"));
        if (!"ADMIN".equals(role) && !job.get("company_id").equals(actor.get("company_id")))
            throw new JobApiException(403, "Job belongs to another company");
        return job;
    }
}
