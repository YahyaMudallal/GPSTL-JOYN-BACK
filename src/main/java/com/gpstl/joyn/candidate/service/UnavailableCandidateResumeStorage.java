package com.gpstl.joyn.candidate.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

public class UnavailableCandidateResumeStorage implements CandidateResumeStorage {

    private static final String MESSAGE =
            "Resume storage is unavailable: configure all required Supabase S3 environment variables "
                    + "or select the local storage provider.";

    @Override
    public String store(Long candidateId, MultipartFile resume) {
        throw unavailable();
    }

    @Override
    public String createPresignedUrl(String storageKey) {
        throw unavailable();
    }

    @Override
    public Optional<StoredCandidateResume> load(String storageKey) {
        throw unavailable();
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, MESSAGE);
    }
}
