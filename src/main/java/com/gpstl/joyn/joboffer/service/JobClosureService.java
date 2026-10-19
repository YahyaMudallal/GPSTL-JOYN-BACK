package com.gpstl.joyn.joboffer.service;

import com.gpstl.joyn.joboffer.repositories.JobAccessRepository;
import com.gpstl.joyn.joboffer.repositories.JobClosureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobClosureService {
    private final JobClosureRepository jobs;
    private final JobAccessService access;
    private final JobAccessRepository audit;
    public JobClosureService(JobClosureRepository jobs, JobAccessService access, JobAccessRepository audit) {
        this.jobs = jobs; this.access = access; this.audit = audit;
    }

    @Transactional
    public void close(long userId, long jobId, String reason) {
        access.authorizedJob(userId, jobId);
        jobs.close(jobId);
        audit.audit(userId, jobId, "CLOSE", reason);
    }
}
