package com.gpstl.joyn.joboffer.service;

import com.gpstl.joyn.joboffer.models.JobOffer;
import com.gpstl.joyn.joboffer.models.JobOfferAudit;
import com.gpstl.joyn.joboffer.models.OfferStatus;
import com.gpstl.joyn.joboffer.repositories.JobOfferAuditRepository;
import com.gpstl.joyn.joboffer.repositories.JobOfferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class JobClosureService {
    private final JobOfferRepository jobs;
    private final JobAccessService access;
    private final JobOfferAuditRepository audit;
    public JobClosureService(JobOfferRepository jobs, JobAccessService access, JobOfferAuditRepository audit) {
        this.jobs = jobs; this.access = access; this.audit = audit;
    }

    @Transactional
    public void close(long userId, long jobId, String reason) {
        JobOffer job = access.authorizedJob(userId, jobId);
        job.setStatus(OfferStatus.CLOSED);
        job.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        jobs.saveAndFlush(job);
        audit.saveAndFlush(new JobOfferAudit(userId, job, "CLOSE", reason));
    }
}
