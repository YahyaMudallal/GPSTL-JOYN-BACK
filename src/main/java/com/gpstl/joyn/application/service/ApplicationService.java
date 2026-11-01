package com.gpstl.joyn.application.service;

import com.gpstl.joyn.application.dto.ApplicationResponse;
import com.gpstl.joyn.application.dto.CreateApplicationRequest;
import com.gpstl.joyn.application.dto.UpdateApplicationStatusRequest;
import com.gpstl.joyn.application.exception.ApplicationException;
import com.gpstl.joyn.application.model.Application;
import com.gpstl.joyn.application.model.ApplicationStatus;
import com.gpstl.joyn.application.repository.ApplicationRepository;
import com.gpstl.joyn.application.repository.ApplicationStatusRepository;
import com.gpstl.joyn.candidate.model.Candidate;
import com.gpstl.joyn.candidate.repository.CandidateRepository;
import com.gpstl.joyn.common.model.User;
import com.gpstl.joyn.common.repository.UserRepository;
import com.gpstl.joyn.joboffer.exception.JobApiException;
import com.gpstl.joyn.joboffer.model.JobOffer;
import com.gpstl.joyn.joboffer.model.OfferStatus;
import com.gpstl.joyn.joboffer.repository.JobOfferRepository;
import com.gpstl.joyn.joboffer.service.JobAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationStatusRepository statusRepository;
    private final CandidateRepository candidateRepository;
    private final UserRepository userRepository;
    private final JobOfferRepository jobOfferRepository;
    private final JobAccessService jobAccessService;

    public ApplicationService(
            ApplicationRepository applicationRepository,
            ApplicationStatusRepository statusRepository,
            CandidateRepository candidateRepository,
            UserRepository userRepository,
            JobOfferRepository jobOfferRepository,
            JobAccessService jobAccessService) {
        this.applicationRepository = applicationRepository;
        this.statusRepository = statusRepository;
        this.candidateRepository = candidateRepository;
        this.userRepository = userRepository;
        this.jobOfferRepository = jobOfferRepository;
        this.jobAccessService = jobAccessService;
    }

    @Transactional
    public ApplicationResponse create(
            Long userId, 
            CreateApplicationRequest request) {
        User actor = activeUser(userId);
        if (!"CANDIDATE".equals(actor.getRole())) {
            throw ApplicationException.forbidden("Only candidates can apply to jobs");
        }

        Candidate candidate = candidateRepository.findByUserId(userId)
                .orElseThrow(() -> ApplicationException.forbidden("Candidate profile is required"));
        JobOffer jobOffer = jobOfferRepository.findActiveByIdForUpdate(request.jobOfferId())
                .orElseThrow(() -> ApplicationException.notFound("Job offer not found"));
        if (jobOffer.getStatus() != OfferStatus.PUBLISHED
                || (jobOffer.getExpiresAt() != null && !jobOffer.getExpiresAt().isAfter(OffsetDateTime.now()))) {
            throw ApplicationException.notFound("Job offer is not available");
        }

        if (applicationRepository.existsByCandidate_IdAndJobOffer_Id(
                candidate.getId(), jobOffer.getId())) {
            throw ApplicationException.conflict("Candidate has already applied to this job offer");
        }

        ApplicationStatus pendingStatus = statusRepository.findByCode("PENDING")
                .orElseThrow(() -> ApplicationException.internalError(
                        "The initial application status is not configured"));
        Application application = new Application(candidate, jobOffer, pendingStatus,
                request.coverMessage(), request.resumeUrl());
        return toResponse(applicationRepository.saveAndFlush(application));
    }

    @Transactional
    public ApplicationResponse updateStatus(
            Long userId, 
            Long applicationId, 
            UpdateApplicationStatusRequest request) {
        activeUser(userId);
        Application application = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> ApplicationException.notFound("Application not found"));

        // A recruiter must belong to the company of this job offer; an administrator can process
        // any application (no company check for ADMIN, see JobAccessService).
        // checkCompanyAccess (not authorizedJob): applications stay manageable after the job is
        // closed (soft-deleted), and updating a status does not need to lock the job offer.
        try {
            jobAccessService.checkCompanyAccess(userId, application.getJobOffer());
        } catch (JobApiException ex) {
            HttpStatus status = HttpStatus.resolve(ex.getStatus());
            if (status == null || status.is5xxServerError()) {
                throw ApplicationException.internalError("Unable to verify access to the job offer");
            }
            throw ApplicationException.fromJobAccessFailure(status, ex.getMessage());
        }

        ApplicationStatus status = statusRepository.findByCode(request.status())
                .orElseThrow(() -> ApplicationException.badRequest("Unknown application status"));

        application.setStatus(status);
        if ("VIEWED".equals(status.getCode()) && application.getViewedAt() == null) {
            application.setViewedAt(OffsetDateTime.now());
        }
        return toResponse(applicationRepository.saveAndFlush(application));
    }

    private static ApplicationResponse toResponse(Application application) {
        return new ApplicationResponse(
                application.getId(),
                application.getCandidate().getId(),
                application.getJobOffer().getId(),
                application.getStatus().getCode(),
                application.getViewedAt(),
                application.getCoverMessage(),
                application.getResumeUrl(),
                application.getCreatedAt(),
                application.getUpdatedAt());
    }

    private User activeUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApplicationException.unauthorized("Authenticated user does not exist"));
        if (!"ACTIVE".equals(user.getStatus())) {
            throw ApplicationException.forbidden("Account is suspended");
        }
        return user;
    }

    /**
     * Parses the authenticated JWT subject as the database user ID.
     */
    public static Long userIdFromSubject(String subject) {
        try {
            long userId = Long.parseLong(subject);
            if (userId <= 0) {
                throw new NumberFormatException("User ID must be positive");
            }
            return userId;
        } catch (NumberFormatException | NullPointerException ex) {
            throw ApplicationException.unauthorized("Invalid authenticated user identity");
        }
    }
}
