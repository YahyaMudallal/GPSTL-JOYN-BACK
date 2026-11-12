
package com.gpstl.joyn.candidate.service;

import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Storage boundary for candidate resumes,
 * shared by local and Supabase S3 adapters.
 */
public interface CandidateResumeStorage {

    Pattern STORAGE_KEY_PATTERN = Pattern.compile(
            "resumes/[1-9][0-9]*/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.pdf");

    String store(Long candidateId, MultipartFile resume);

    String createPresignedUrl(String storageKey);

    Optional<StoredCandidateResume> load(String storageKey);

    // Delete a stored CV.
    void delete(String storageKey);

    default String createPresignedUrlIfValid(String storageKey) {
        if (storageKey == null) {
            return null;
        }

        if (!STORAGE_KEY_PATTERN.matcher(storageKey).matches()) {
            LoggerFactory.getLogger(CandidateResumeStorage.class)
                    .warn("Skipping presigned URL for an unsupported legacy resume storage key");
            return null;
        }

        return createPresignedUrl(storageKey);
    }

    record StoredCandidateResume(Resource resource, MediaType mediaType) {
    }
}
