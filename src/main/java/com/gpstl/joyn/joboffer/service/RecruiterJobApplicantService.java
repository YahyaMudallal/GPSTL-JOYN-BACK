package com.gpstl.joyn.joboffer.service;

import com.gpstl.joyn.candidate.dto.CandidateProfileResponse;
import com.gpstl.joyn.candidate.repository.CandidateRepository;
import com.gpstl.joyn.candidate.service.CandidateResumeStorage;
import com.gpstl.joyn.joboffer.dto.RecruiterJobApplicantItem;
import com.gpstl.joyn.joboffer.dto.RecruiterJobApplicantsPageResponse;
import com.gpstl.joyn.joboffer.exception.JobApiException;
import com.gpstl.joyn.joboffer.model.JobOffer;
import com.gpstl.joyn.joboffer.repository.JobOfferRepository;
import com.gpstl.joyn.joboffer.repository.RecruiterJobApplicantProjection;
import com.gpstl.joyn.joboffer.repository.RecruiterJobApplicantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

    public RecruiterJobApplicantService(
            JobOfferRepository jobOffers,
            JobAccessService jobAccess,
            RecruiterJobApplicantRepository applicants,
            CandidateRepository candidates,
            CandidateResumeStorage resumeStorage) {
        this.jobOffers = jobOffers;
        this.jobAccess = jobAccess;
        this.applicants = applicants;
        this.candidates = candidates;
        this.resumeStorage = resumeStorage;
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

        Page<RecruiterJobApplicantProjection> results = applicants.findApplicants(
                jobId,
                PageRequest.of(page - 1, limit));

        List<RecruiterJobApplicantItem> content = results.getContent().stream()
                .map(result -> toItem(result))
                .toList();
        return new RecruiterJobApplicantsPageResponse(
            content,
            results.getNumber() + 1,
            results.getSize(),
            results.getTotalElements(),
            results.getTotalPages(),
            results.hasNext());
    }

    private RecruiterJobApplicantItem toItem(RecruiterJobApplicantProjection result) {
        CandidateProfileResponse profile = candidates.findProfileById(result.getCandidateId())
                .map(candidate -> CandidateProfileResponse.forRecruiter(
                        candidate,
                        resumeStorage.createPresignedUrlIfValid(candidate.getResumeUrl())))
                .orElseThrow(() -> new JobApiException(404, "Candidate profile not found"));

        Integer score = result.getMatchScore() == null ? null : result.getMatchScore().intValue();
        RecruiterJobApplicantItem.ScoreBreakdown breakdown = score == null ? null
                : new RecruiterJobApplicantItem.ScoreBreakdown(
                    toInt(result.getSkillScore()),
                    toInt(result.getExperienceScore()),
                    toInt(result.getEducationScore()),
                    toInt(result.getLocationScore()),
                    toInt(result.getAvailabilityScore()),
                    result.getDistanceKm());
        return new RecruiterJobApplicantItem(
            result.getApplicationId(),
            result.getApplicationStatus(),
            result.getAppliedAt(),
            score,
            breakdown,
            profile);
    }

    private static Integer toInt(Short value) {
        return value == null ? null : value.intValue();
    }
}
