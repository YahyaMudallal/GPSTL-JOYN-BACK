package com.gpstl.joyn.candidate.service;

import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public class SupabaseCandidateResumeStorage implements CandidateResumeStorage {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final Duration presignedUrlDuration;

    public SupabaseCandidateResumeStorage(
            S3Client s3Client,
            S3Presigner s3Presigner,
            String bucket,
            Duration presignedUrlDuration
    ) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.presignedUrlDuration = presignedUrlDuration;
    }

    @Override
    public String store(Long candidateId, MultipartFile resume) {
        if (candidateId == null || candidateId <= 0) {
            throw new IllegalArgumentException("Candidate ID must be positive");
        }
        String key = "resumes/" + candidateId + "/" + UUID.randomUUID() + ".pdf";
        try (var input = resume.getInputStream()) {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(MediaType.APPLICATION_PDF_VALUE)
                            .contentDisposition("inline; filename=\"resume.pdf\"")
                            .cacheControl("private, no-store")
                            .build(),
                    RequestBody.fromInputStream(input, resume.getSize())
            );
            return key;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read candidate resume upload", exception);
        }
    }

    @Override
    public String createPresignedUrl(String storageKey) {
        validateStorageKey(storageKey);
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey)
                .responseContentType(MediaType.APPLICATION_PDF_VALUE)
                .responseContentDisposition("inline; filename=\"resume.pdf\"")
                .responseCacheControl("private, no-store")
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(presignedUrlDuration)
                .getObjectRequest(getObjectRequest)
                .build();
        return s3Presigner.presignGetObject(presignRequest).url().toExternalForm();
    }

    @Override
    public Optional<StoredCandidateResume> load(String storageKey) {
        validateStorageKey(storageKey);
        return Optional.empty();
    }

    private static void validateStorageKey(String storageKey) {
        if (storageKey == null || !CandidateResumeStorage.STORAGE_KEY_PATTERN.matcher(storageKey).matches()) {
            throw new IllegalArgumentException("Invalid resume storage key");
        }
    }
}
