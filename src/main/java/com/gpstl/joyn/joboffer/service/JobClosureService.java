
package com.gpstl.joyn.joboffer.service;

import com.gpstl.joyn.application.model.Application;
import com.gpstl.joyn.application.repository.ApplicationRepository;
import com.gpstl.joyn.joboffer.exception.JobApiException;
import com.gpstl.joyn.joboffer.model.JobOffer;
import com.gpstl.joyn.joboffer.model.JobOfferAudit;
import com.gpstl.joyn.joboffer.model.OfferStatus;
import com.gpstl.joyn.joboffer.repository.JobOfferAuditRepository;
import com.gpstl.joyn.joboffer.repository.JobOfferRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

@Service
public class JobClosureService {

    private final JobOfferRepository jobs;
    private final JobAccessService access;
    private final JobOfferAuditRepository audit;
    private final ApplicationRepository applications;

    public JobClosureService(
            JobOfferRepository jobs,
            JobAccessService access,
            JobOfferAuditRepository audit,
            ApplicationRepository applications) {

        this.jobs = jobs;
        this.access = access;
        this.audit = audit;
        this.applications = applications;
    }

    // Existing functionality: close an offer
    @Transactional
    public void close(long userId, long jobId, String reason) {
        JobOffer job = access.authorizedJob(userId, jobId);

        // Preserve historical applications, matches and conversations.
        job.setStatus(OfferStatus.CLOSED);
        job.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));

        jobs.saveAndFlush(job);

        audit.saveAndFlush(new JobOfferAudit(userId, job, "CLOSE", reason));
    }

    // R74: archive an offer with the recruitment outcome
    @Transactional
    public void archive(long userId, long jobId, String outcome, Long hiredApplicationId) {

        JobOffer job = access.authorizedJob(userId, jobId);

        if (outcome == null
                || !Set.of("FILLED", "NOT_FILLED", "ONGOING").contains(outcome)) {
            throw new JobApiException(400, "Invalid recruitment outcome");
        }

        Application hired = null;

        if ("FILLED".equals(outcome)) {

            if (hiredApplicationId == null) {
                throw new JobApiException(400, "Hired application is required");
            }

            hired = applications.findById(hiredApplicationId)
                    .orElseThrow(() -> new JobApiException(400, "Application not found"));

            if (!job.getId().equals(hired.getJobOffer().getId())
                    || !"ACCEPTED".equals(hired.getStatus().getCode())) {
                throw new JobApiException(400, "Application must be accepted for this job");
            }

        } else if (hiredApplicationId != null) {
            throw new JobApiException(400, "Hired application is only allowed for FILLED");
        }

        job.setStatus(OfferStatus.ARCHIVED);
        job.setRecruitmentOutcome(outcome);
        job.setHiredApplication(hired);
        job.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));

        jobs.saveAndFlush(job);

        audit.saveAndFlush(
                new JobOfferAudit(userId, job, "CLOSE", "Archived: " + outcome)
        );
    }
}
