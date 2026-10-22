package com.gpstl.joyn.candidate.service;

import com.gpstl.joyn.candidate.dto.CandidateRegisterRequest;
import com.gpstl.joyn.candidate.dto.CandidateRegisterResponse;
import com.gpstl.joyn.candidate.model.Candidate;
import com.gpstl.joyn.candidate.repository.CandidateRepository;
import com.gpstl.joyn.common.model.User;
import com.gpstl.joyn.common.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

@Service
public class RegistrationService {

    private static final long MAX_RESUME_SIZE = 5L * 1024 * 1024;

    private final UserRepository userRepository;
    private final CandidateRepository candidateRepository;
    private final PasswordEncoder passwordEncoder;
    private final CandidateResumeStorage resumeStorage;

    public RegistrationService(
            UserRepository userRepository,
            CandidateRepository candidateRepository,
            PasswordEncoder passwordEncoder,
            CandidateResumeStorage resumeStorage) {
        this.userRepository = userRepository;
        this.candidateRepository = candidateRepository;
        this.passwordEncoder = passwordEncoder;
        this.resumeStorage = resumeStorage;
    }

    @Transactional
    public CandidateRegisterResponse register(
            CandidateRegisterRequest request,
            MultipartFile resume) {
        validateRegistration(request, resume);

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new CandidateEmailAlreadyRegisteredException();
        }

        // The storage key is saved in the profile and exposed through the resume route.
        String resumeUrl = resume == null || resume.isEmpty()
                ? null
                : "/api/v1/candidates/resumes/" + resumeStorage.store(resume);

        User user = userRepository.saveAndFlush(new User(
                email,
                passwordEncoder.encode(request.password()),
                "CANDIDATE"));

        Candidate candidate = candidateRepository.saveAndFlush(new Candidate(
                user,
                request.firstName().trim(),
                request.lastName().trim(),
                resumeUrl));

        return new CandidateRegisterResponse(
                candidate.getId(),
                user.getId(),
                user.getEmail(),
                candidate.getFirstName(),
                candidate.getLastName(),
                candidate.getResumeUrl());
    }

    public CandidateResumeStorage.StoredCandidateResume loadResume(String storageKey) {
        return resumeStorage.load(storageKey)
                .orElseThrow(CandidateResumeNotFoundException::new);
    }

    private void validateRegistration(
            CandidateRegisterRequest request,
            MultipartFile resume) {
        if (request == null
                || isBlank(request.firstName())
                || isBlank(request.lastName())
                || isBlank(request.email())
                || request.password() == null
                || request.password().length() < 12) {
            throw new IllegalArgumentException("Invalid registration information");
        }

        if (!request.email().trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("Invalid email address");
        }

        if (resume != null && !resume.isEmpty() && resume.getSize() > MAX_RESUME_SIZE) {
            throw new IllegalArgumentException("Resume must not exceed 5 MB");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
