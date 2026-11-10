package com.gpstl.joyn.candidate.service;

import com.gpstl.joyn.candidate.dto.CandidateRegisterRequest;
import com.gpstl.joyn.candidate.dto.CandidateRegisterResponse;
import com.gpstl.joyn.candidate.dto.CandidateResumeResponse;
import com.gpstl.joyn.candidate.exception.CandidateResumeNotFoundException;
import com.gpstl.joyn.candidate.model.Candidate;
import com.gpstl.joyn.candidate.repository.CandidateRepository;
import com.gpstl.joyn.common.geocoding.AddressGeocodingService;
import com.gpstl.joyn.common.geocoding.Coordinates;
import com.gpstl.joyn.common.model.User;
import com.gpstl.joyn.common.repository.UserRepository;
import com.gpstl.joyn.common.service.UserAccountService;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class CandidateService {

    private static final long MAX_RESUME_SIZE = 5L * 1024 * 1024;
    private static final Tika FILE_TYPE_DETECTOR = new Tika();

    private final CandidateRepository candidateRepository;
    private final UserRepository userRepository;
    private final UserAccountService userAccountService;
    private final CandidateResumeStorage resumeStorage;
    private final AddressGeocodingService geocodingService;

    public CandidateService(
            CandidateRepository candidateRepository,
            UserRepository userRepository,
            UserAccountService userAccountService,
            CandidateResumeStorage resumeStorage,
            AddressGeocodingService geocodingService) {
        this.candidateRepository = candidateRepository;
        this.userRepository = userRepository;
        this.userAccountService = userAccountService;
        this.resumeStorage = resumeStorage;
        this.geocodingService = geocodingService;
    }

    @Transactional
    public CandidateRegisterResponse register(
            CandidateRegisterRequest request,
            MultipartFile resume) {
        validateCandidateInfo(request, resume);
        Coordinates coordinates = geocodingService.geocode(request.location()).orElse(null);

        User user = userAccountService.createUserAccount(
                request.email(),
                request.password(),
                "CANDIDATE"
        );

        Candidate candidate = new Candidate(
                user,
                request.firstName().trim(),
                request.lastName().trim(),
                null);
        candidate.setLocation(blankToNull(request.location()));
        setCoordinates(candidate, coordinates);
        candidate = candidateRepository.saveAndFlush(candidate);
        if (resume != null && !resume.isEmpty()) {
            String storageKey = resumeStorage.store(candidate.getId(), resume);
            candidate.setResumeUrl(storageKey);
            candidate = candidateRepository.saveAndFlush(candidate);
        }

        return new CandidateRegisterResponse(
                candidate.getId(),
                user.getId(),
                user.getEmail(),
                candidate.getFirstName(),
                candidate.getLastName(),
                signedResumeUrl(candidate.getResumeUrl())
        );
    }

    @Transactional
    public CandidateResumeResponse updateResume(Long userId, MultipartFile resume) {
        validatePdf(resume, true);
        Candidate candidate = candidateRepository.findByUserIdForUpdate(userId)
                .orElseThrow(CandidateResumeNotFoundException::new);
        User user = userRepository.findById(userId)
                .orElseThrow(CandidateResumeNotFoundException::new);
        if (!"CANDIDATE".equals(user.getRole()) || !"ACTIVE".equals(user.getStatus())) {
            throw new CandidateResumeNotFoundException();
        }

        String storageKey = resumeStorage.store(candidate.getId(), resume);
        candidate.setResumeUrl(storageKey);
        candidateRepository.saveAndFlush(candidate);
        return new CandidateResumeResponse(resumeStorage.createPresignedUrl(storageKey));
    }

    @Transactional(readOnly = true)
    public CandidateResumeStorage.StoredCandidateResume loadResume(
            Long viewerUserId,
            Long candidateId,
            String fileName) {
        User viewer = userRepository.findById(viewerUserId)
                .orElseThrow(CandidateResumeNotFoundException::new);
        Candidate candidate = candidateRepository.findProfileById(candidateId)
                .orElseThrow(CandidateResumeNotFoundException::new);
        boolean canView = switch (viewer.getRole()) {
            case "ADMIN" -> "ACTIVE".equals(viewer.getStatus());
            case "CANDIDATE" -> "ACTIVE".equals(viewer.getStatus())
                    && candidate.getUser().getId().equals(viewerUserId);
            case "RECRUITER" -> "ACTIVE".equals(viewer.getStatus())
                    && candidate.isVisibleToRecruiters()
                    && "ACTIVE".equals(candidate.getUser().getStatus());
            default -> false;
        };
        if (!canView) {
            throw new CandidateResumeNotFoundException();
        }
        String storageKey = "resumes/" + candidateId + "/" + fileName;
        return resumeStorage.load(storageKey)
                .orElseThrow(CandidateResumeNotFoundException::new);
    }

    private void validateCandidateInfo(
            CandidateRegisterRequest request,
            MultipartFile resume) {
        if (request == null
                || isBlank(request.firstName())
                || isBlank(request.lastName())) {
            throw new IllegalArgumentException("Les informations du candidat sont incomplètes.");
        }

        if (resume != null && !resume.isEmpty() && resume.getSize() > MAX_RESUME_SIZE) {
            throw new IllegalArgumentException("Resume must not exceed 5 MB");
        }
        if (resume != null && !resume.isEmpty()) {
            validatePdf(resume, false);
        }
    }

    private String signedResumeUrl(String storageKey) {
        return resumeStorage.createPresignedUrlIfValid(storageKey);
    }

    private static void validatePdf(MultipartFile resume, boolean required) {
        if (resume == null || resume.isEmpty()) {
            if (required) {
                throw new IllegalArgumentException("A PDF resume is required");
            }
            return;
        }
        if (resume.getSize() > MAX_RESUME_SIZE) {
            throw new IllegalArgumentException("Resume must not exceed 5 MB");
        }
        try (var input = resume.getInputStream()) {
            if (!"application/pdf".equals(FILE_TYPE_DETECTOR.detect(input))) {
                throw new IllegalArgumentException("Resume must be a valid PDF file");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read candidate resume upload", exception);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void setCoordinates(Candidate candidate, Coordinates coordinates) {
        candidate.setLatitude(coordinates == null ? null : coordinates.latitude());
        candidate.setLongitude(coordinates == null ? null : coordinates.longitude());
    }
}