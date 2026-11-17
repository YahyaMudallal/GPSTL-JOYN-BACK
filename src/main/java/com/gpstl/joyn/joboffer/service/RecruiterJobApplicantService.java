package com.gpstl.joyn.joboffer.service;

import com.gpstl.joyn.candidate.dto.CandidateProfileResponse;
import com.gpstl.joyn.candidate.service.CandidateRecommendationService;
import com.gpstl.joyn.candidate.repository.CandidateRepository;
import com.gpstl.joyn.candidate.service.CandidateResumeStorage;
import com.gpstl.joyn.joboffer.dto.RecruiterJobApplicantItem;
import com.gpstl.joyn.joboffer.dto.RecruiterJobApplicantsPageResponse;
import com.gpstl.joyn.joboffer.exception.JobApiException;
import com.gpstl.joyn.joboffer.model.JobOffer;
import com.gpstl.joyn.joboffer.repository.JobOfferRepository;
import com.gpstl.joyn.joboffer.repository.RecruiterJobApplicantProjection;
import com.gpstl.joyn.joboffer.repository.RecruiterJobApplicantRepository;
import com.gpstl.joyn.user.service.UserProfilePictureStorage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Comparator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RecruiterJobApplicantService {

    private static final int MAX_PAGE_SIZE = 50;

    private final JobOfferRepository jobOffers;
    private final JobAccessService jobAccess;
    private final RecruiterJobApplicantRepository applicants;
    private final CandidateRepository candidates;
    private final CandidateResumeStorage resumeStorage;
    private final UserProfilePictureStorage profilePictureStorage;

    public RecruiterJobApplicantService(
            JobOfferRepository jobOffers,
            JobAccessService jobAccess,
            RecruiterJobApplicantRepository applicants,
            CandidateRepository candidates,
            CandidateResumeStorage resumeStorage,
            UserProfilePictureStorage profilePictureStorage) {
        this.jobOffers = jobOffers;
        this.jobAccess = jobAccess;
        this.applicants = applicants;
        this.candidates = candidates;
        this.resumeStorage = resumeStorage;
        this.profilePictureStorage = profilePictureStorage;
    }

    @Transactional(readOnly = true)
    public RecruiterJobApplicantsPageResponse findApplicants(
            long recruiterUserId,
            long jobId,
            int page,
            int limit) {
        if (jobId <= 0) {
            throw new JobApiException(400, "jobId must be a positive integer");
        }
        if (page < 1) {
            throw new JobApiException(400, "page must be greater than or equal to 1");
        }
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new JobApiException(400, "limit must be between 1 and " + MAX_PAGE_SIZE);
        }
        // Check the offer first so a missing ID consistently returns 404; closed offers remain
        // readable because their applications and scores are still relevant to the recruiter.
        JobOffer jobOffer = jobOffers.findById(jobId)
                .orElseThrow(() -> new JobApiException(404, "Job not found"));
        jobAccess.checkCompanyAccess(recruiterUserId, jobOffer);

        // Score every visible applicant before pagination: seed-created applications may
        // have no cached recommendation, and profile/offer edits can invalidate old scores.
        Page<RecruiterJobApplicantProjection> results = applicants.findApplicants(jobId, Pageable.unpaged());
        List<RecruiterJobApplicantItem> ranked = results.getContent().stream()
                .map(result -> toItem(result, jobOffer))
                .sorted(Comparator.comparing(RecruiterJobApplicantItem::matchScore,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(RecruiterJobApplicantItem::appliedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(RecruiterJobApplicantItem::applicationId, Comparator.reverseOrder()))
                .toList();
        long offset = (long) (page - 1) * limit;
        List<RecruiterJobApplicantItem> content = offset >= ranked.size() ? List.of()
                : ranked.subList((int) offset, (int) Math.min(offset + limit, ranked.size()));
        return new RecruiterJobApplicantsPageResponse(content, page, limit, ranked.size(),
                (int) Math.ceil((double) ranked.size() / limit), offset + limit < ranked.size());
    }

    private RecruiterJobApplicantItem toItem(RecruiterJobApplicantProjection result, JobOffer offer) {
        var candidate = candidates.findProfileById(result.getCandidateId())
                .orElseThrow(() -> new JobApiException(404, "Candidate profile not found"));
        CandidateProfileResponse profile = CandidateProfileResponse.forRecruiter(candidate,
                resumeStorage.createPresignedUrlIfValid(candidate.getResumeUrl()),
                profilePictureStorage.createPresignedUrlIfValid(candidate.getPhotoUrl()));
        var calculated = CandidateRecommendationService.calculate(candidate, offer);
        Integer score = calculated.matchScore();
        var breakdown = new RecruiterJobApplicantItem.ScoreBreakdown(calculated.skillScore(),
                calculated.experienceScore(), calculated.educationScore(), calculated.locationScore(),
                calculated.availabilityScore(), calculated.distanceKm() == null ? null : java.math.BigDecimal.valueOf(calculated.distanceKm()));
        return new RecruiterJobApplicantItem(
            result.getApplicationId(),
            result.getApplicationStatus(),
            result.getAppliedAt(),
            score,
            breakdown,
            profile);
    }

}
