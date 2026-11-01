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

/**
 * Vérifie les droits d'un recruteur ou d'un administrateur sur une offre :
 *  - authorizedJob : modification ou clôture de l'offre (offre non supprimée, verrouillée) ;
 *  - checkCompanyAccess : gestion de ce qui reste rattaché à l'offre (candidatures),
 *    y compris après sa clôture.
 */
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
        String role = actorRole(userId);
        JobOffer job = offers.findActiveByIdForUpdate(jobId)
            .orElseThrow(() -> new JobApiException(404, "Job not found"));
        checkSameCompany(userId, role, job);
        return job;
    }

    /**
     * Mêmes règles de rôle et de société, mais sans exclure les offres supprimées logiquement et
     * sans verrou : une offre close garde ses candidatures, que l'entreprise doit pouvoir traiter.
     * L'offre est fournie par l'appelant (par exemple celle d'une candidature déjà chargée).
     */
    public void checkCompanyAccess(long userId, JobOffer job) {
        checkSameCompany(userId, actorRole(userId), job);
    }

    // L’identité vient du JWT vérifié, mais le rôle et l’état du compte sont relus en base.
    private String actorRole(long userId) {
        User actor = users.findById(userId)
            .orElseThrow(() -> new JobApiException(401, "Authenticated user does not exist"));
        if (!"ACTIVE".equals(actor.getStatus())) throw new JobApiException(403, "Account is suspended");
        String role = actor.getRole();
        if (!"ADMIN".equals(role) && !"RECRUITER".equals(role))
            throw new JobApiException(403, "Insufficient permissions");
        return role;
    }

    // Un administrateur accède à toutes les offres ; un recruteur, à celles de sa société.
    private void checkSameCompany(long userId, String role, JobOffer job) {
        if ("ADMIN".equals(role)) return;
        Recruiter recruiter = recruiters.findByUserId(userId)
            .orElseThrow(() -> new JobApiException(403, "Recruiter profile is required"));
        if (!Objects.equals(job.getCompany().getId(), recruiter.getCompany().getId()))
            throw new JobApiException(403, "Job belongs to another company");
    }
}
