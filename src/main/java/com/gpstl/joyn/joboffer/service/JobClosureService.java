package com.gpstl.joyn.joboffer.service;

import com.gpstl.joyn.joboffer.model.JobOffer;
import com.gpstl.joyn.joboffer.model.JobOfferAudit;
import com.gpstl.joyn.joboffer.model.OfferStatus;
import com.gpstl.joyn.joboffer.repository.JobOfferAuditRepository;
import com.gpstl.joyn.joboffer.repository.JobOfferRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Clôture l’offre et enregistre l’audit ensemble, sans effacer les relations historiques. */
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
        // Aucun delete() : candidatures, matches et conversations gardent leur offre associée.
        job.setStatus(OfferStatus.CLOSED);
        job.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        jobs.saveAndFlush(job);
        // Un échec de l’audit annule aussi la clôture grâce à la transaction commune.
        audit.saveAndFlush(new JobOfferAudit(userId, job, "CLOSE", reason));
    }
}
