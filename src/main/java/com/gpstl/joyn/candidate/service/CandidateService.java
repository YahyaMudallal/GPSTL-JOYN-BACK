package com.gpstl.joyn.candidate.service;

import com.gpstl.joyn.candidate.dto.CandidateRegisterRequest;
import com.gpstl.joyn.candidate.dto.CandidateRegisterResponse;
import com.gpstl.joyn.candidate.exception.CandidateResumeNotFoundException;
import com.gpstl.joyn.candidate.model.Candidate;
import com.gpstl.joyn.candidate.repository.CandidateRepository;
import com.gpstl.joyn.common.model.User;
import com.gpstl.joyn.common.service.UserAccountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CandidateService {

    private static final long MAX_RESUME_SIZE = 5L * 1024 * 1024;

    private final CandidateRepository candidateRepository;
    private final UserAccountService userAccountService;
    private final CandidateResumeStorage resumeStorage;

    public CandidateService(
            CandidateRepository candidateRepository,
            UserAccountService userAccountService,
            CandidateResumeStorage resumeStorage) {
        this.candidateRepository = candidateRepository;
        this.userAccountService = userAccountService;
        this.resumeStorage = resumeStorage;
    }

    @Transactional
    public CandidateRegisterResponse register(
            CandidateRegisterRequest request,
            MultipartFile resume) {
        validateCandidateInfo(request, resume);

        User user = userAccountService.createUserAccount(
                request.email(),
                request.password(),
                "CANDIDATE"
        );

        String resumeUrl = (resume == null || resume.isEmpty())
                ? null
                : "/api/v1/candidates/resumes/" + resumeStorage.store(resume);

        Candidate candidate = candidateRepository.saveAndFlush(new Candidate(
                user,
                request.firstName().trim(),
                request.lastName().trim(),
                resumeUrl
        ));

        return new CandidateRegisterResponse(
                candidate.getId(),
                user.getId(),
                user.getEmail(),
                candidate.getFirstName(),
                candidate.getLastName(),
                candidate.getResumeUrl()
        );
    }

    public CandidateResumeStorage.StoredCandidateResume loadResume(String storageKey) {
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
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}