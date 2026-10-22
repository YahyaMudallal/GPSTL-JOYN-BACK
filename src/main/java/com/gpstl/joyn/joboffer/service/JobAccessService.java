package com.gpstl.joyn.joboffer.service;

import com.gpstl.joyn.common.model.User;
import com.gpstl.joyn.common.repository.UserRepository;
import com.gpstl.joyn.joboffer.exception.JobApiException;
import com.gpstl.joyn.joboffer.model.JobOffer;
import com.gpstl.joyn.joboffer.repository.JobOfferRepository;
import com.gpstl.joyn.recruiter.model.Recruiter;
import com.gpstl.joyn.recruiter.repository.RecruiterRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.util.Objects;

/** Vérifie les droits sur une offre avant sa modification ou sa clôture. */
@Service
public class JobAccessService {
    private final UserRepository users;
    private final RecruiterRepository recruiters;
    private final JobOfferRepository offers;

    public JobAccessService(UserRepository users, RecruiterRepository recruiters, JobOfferRepository offers) {
        this.users = users;
        this.recruiters = recruiters;
        this.offers = offers;
    }

    // Réutilise la transaction du service appelant : le verrou reste détenu jusqu’à sa fin.
    @Transactional(propagation = Propagation.MANDATORY)
    public JobOffer authorizedJob(long userId, long jobId) {
        // L’identité vient du JWT vérifié, mais le rôle et l’état du compte sont relus en base.
        User actor = users.findById(userId)
            .orElseThrow(() -> new JobApiException(401, "Authenticated user does not exist"));
        if (!"ACTIVE".equals(actor.getStatus())) throw new JobApiException(403, "Account is suspended");
        String role = actor.getRole();
        if (!"ADMIN".equals(role) && !"RECRUITER".equals(role))
            throw new JobApiException(403, "Insufficient permissions");
        JobOffer job = offers.findActiveByIdForUpdate(jobId)
            .orElseThrow(() -> new JobApiException(404, "Job not found"));
        if (!"ADMIN".equals(role)) {
            Recruiter recruiter = recruiters.findByUserId(userId)
                .orElseThrow(() -> new JobApiException(403, "Recruiter profile is required"));
            if (!Objects.equals(job.getCompany().getId(), recruiter.getCompany().getId()))
                throw new JobApiException(403, "Job belongs to another company");
        }
        return job;
    }
}
