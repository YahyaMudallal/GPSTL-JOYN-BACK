package com.gpstl.joyn.application.service;

import com.gpstl.joyn.application.dto.ApplicationResponse;
import com.gpstl.joyn.application.dto.CandidateApplicationResponse;
import com.gpstl.joyn.application.dto.CandidateApplicationsPageResponse;
import com.gpstl.joyn.application.dto.CreateApplicationRequest;
import com.gpstl.joyn.application.dto.UpdateApplicationStatusRequest;
import com.gpstl.joyn.application.exception.ApplicationException;
import com.gpstl.joyn.application.model.Application;
import com.gpstl.joyn.application.model.ApplicationStatus;
import com.gpstl.joyn.application.repository.ApplicationRepository;
import com.gpstl.joyn.application.repository.ApplicationStatusRepository;
import com.gpstl.joyn.application.repository.CandidateApplicationHistoryProjection;
import com.gpstl.joyn.candidate.model.Candidate;
import com.gpstl.joyn.candidate.repository.CandidateRepository;
import com.gpstl.joyn.candidate.service.CandidateResumeStorage;
import com.gpstl.joyn.common.model.User;
import com.gpstl.joyn.common.repository.UserRepository;
import com.gpstl.joyn.joboffer.exception.JobApiException;
import com.gpstl.joyn.joboffer.model.JobOffer;
import com.gpstl.joyn.joboffer.model.OfferStatus;
import com.gpstl.joyn.joboffer.repository.JobOfferRepository;
import com.gpstl.joyn.joboffer.service.JobAccessService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

@Service
public class ApplicationService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final Set<String> APPLICATION_STATUSES = Set.of(
            "PENDING",
            "VIEWED",
            "MATCHED", 
            "IN_REVIEW", 
            "INTERVIEW", 
            "ACCEPTED", 
            "REJECTED", 
            "WITHDRAWN");

    private final ApplicationRepository applicationRepository;
    private final ApplicationStatusRepository statusRepository;
    private final CandidateRepository candidateRepository;
    private final UserRepository userRepository;
    private final JobOfferRepository jobOfferRepository;
    private final JobAccessService jobAccessService;
    private final CandidateResumeStorage resumeStorage;

    public ApplicationService(
            ApplicationRepository applicationRepository,
            ApplicationStatusRepository statusRepository,
            CandidateRepository candidateRepository,
            UserRepository userRepository,
            JobOfferRepository jobOfferRepository,
            JobAccessService jobAccessService,
            CandidateResumeStorage resumeStorage) {
        this.applicationRepository = applicationRepository;
        this.statusRepository = statusRepository;
        this.candidateRepository = candidateRepository;
        this.userRepository = userRepository;
        this.jobOfferRepository = jobOfferRepository;
        this.jobAccessService = jobAccessService;
        this.resumeStorage = resumeStorage;
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
        Application application = new Application(
            candidate, 
            jobOffer, 
            pendingStatus,
            request.coverMessage(),
            candidate.getResumeUrl());
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

    /**
     * Returns only the authenticated candidate's applications, with bounded pagination.
     */
    @Transactional(readOnly = true)
    public CandidateApplicationsPageResponse findCandidateHistory(
            Long userId,
            int page,
            int limit,
            String status,
            String sort) {
        User user = activeUser(userId);
        if (!"CANDIDATE".equals(user.getRole())) {
            throw ApplicationException.forbidden("Only candidates can view application history");
        }
        candidateRepository.findByUserId(userId)
                .orElseThrow(() -> ApplicationException.forbidden("Candidate profile is required"));

        if (page < 1) {
            throw ApplicationException.badRequest("Page must be greater than or equal to 1");
        }
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw ApplicationException.badRequest("Limit must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (status != null && !APPLICATION_STATUSES.contains(status)) {
            throw ApplicationException.badRequest("Unknown application status");
        }

        boolean ascending = switch (sort) {
            case "created_at,asc" -> true;
            case "created_at,desc" -> false;
            default -> throw ApplicationException.badRequest(
                    "Sort must be either created_at,asc or created_at,desc");
        };

        Page<CandidateApplicationHistoryProjection> results = applicationRepository.findCandidateHistory(
                userId, 
                status, 
                ascending, 
                PageRequest.of(page - 1, limit));
        List<CandidateApplicationResponse> content = results.getContent().stream()
                .map(this::toCandidateApplicationResponse)
                .toList();
        return new CandidateApplicationsPageResponse(
                content,
                page,
                limit,
                results.getTotalElements(),
                results.getTotalPages(),
                results.hasNext());
    }

    private CandidateApplicationResponse toCandidateApplicationResponse(
            CandidateApplicationHistoryProjection application) {
        return new CandidateApplicationResponse(
                application.getId(),
                application.getJobOfferId(),
                application.getJobTitle(),
                application.getCompanyId(),
                application.getCompanyName(),
                application.getStatus(),
                toOffsetDateTime(application.getViewedAt()),
                application.getCoverMessage(),
                signedResumeUrl(application.getResumeUrl()),
                toOffsetDateTime(application.getCreatedAt()),
                toOffsetDateTime(application.getUpdatedAt()),
                application.getConversationId());
    }

    private static OffsetDateTime toOffsetDateTime(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    private ApplicationResponse toResponse(Application application) {
        return new ApplicationResponse(
                application.getId(),
                application.getCandidate().getId(),
                application.getJobOffer().getId(),
                application.getStatus().getCode(),
                application.getViewedAt(),
                application.getCoverMessage(),
                signedResumeUrl(application.getResumeUrl()),
                application.getCreatedAt(),
                application.getUpdatedAt());
    }

    private String signedResumeUrl(String storageKey) {
        return resumeStorage.createPresignedUrlIfValid(storageKey);
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
