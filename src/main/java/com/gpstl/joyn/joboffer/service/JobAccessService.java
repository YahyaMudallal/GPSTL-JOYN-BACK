package com.gpstl.joyn.joboffer.service;

import com.gpstl.joyn.common.models.User;
import com.gpstl.joyn.common.repositories.UserRepository;
import com.gpstl.joyn.joboffer.exception.JobApiException;
import com.gpstl.joyn.joboffer.models.JobOffer;
import com.gpstl.joyn.joboffer.repositories.JobOfferRepository;
import com.gpstl.joyn.recruiter.models.RecruiterProfile;
import com.gpstl.joyn.recruiter.repositories.RecruiterProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.util.Objects;

@Service
public class JobAccessService {
    private final UserRepository users;
    private final RecruiterProfileRepository recruiters;
    private final JobOfferRepository offers;

    public JobAccessService(UserRepository users, RecruiterProfileRepository recruiters, JobOfferRepository offers) {
        this.users = users;
        this.recruiters = recruiters;
        this.offers = offers;
    }

    // The caller owns the transaction so the JPA row lock covers the complete mutation.
    @Transactional(propagation = Propagation.MANDATORY)
    public JobOffer authorizedJob(long userId, long jobId) {
        User actor = users.findById(userId)
            .orElseThrow(() -> new JobApiException(401, "Authenticated user does not exist"));
        if (!"ACTIVE".equals(actor.getStatus())) throw new JobApiException(403, "Account is suspended");
        String role = actor.getRole();
        if (!"ADMIN".equals(role) && !"RECRUITER".equals(role))
            throw new JobApiException(403, "Insufficient permissions");
        JobOffer job = offers.findActiveByIdForUpdate(jobId)
            .orElseThrow(() -> new JobApiException(404, "Job not found"));
        if (!"ADMIN".equals(role)) {
            RecruiterProfile recruiter = recruiters.findByUserId(userId)
                .orElseThrow(() -> new JobApiException(403, "Recruiter profile is required"));
            if (!Objects.equals(job.getCompany().getId(), recruiter.getCompany().getId()))
                throw new JobApiException(403, "Job belongs to another company");
        }
        return job;
    }
}
