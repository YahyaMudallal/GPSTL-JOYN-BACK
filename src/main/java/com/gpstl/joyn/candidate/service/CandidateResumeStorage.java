package com.gpstl.joyn.candidate.service;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

/**
 * Storage boundary for candidate resumes. Replace the local adapter with a
 * media-server implementation without changing registration or API behavior.
 */
public interface CandidateResumeStorage {

    String store(MultipartFile resume);

    Optional<StoredCandidateResume> load(String storageKey);

    record StoredCandidateResume(Resource resource, MediaType mediaType) {
    }
}
