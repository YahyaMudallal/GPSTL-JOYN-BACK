package com.gpstl.joyn.candidate.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/**
 * Local development adapter until the deployment's media server is selected.
 */
@Component
@ConditionalOnProperty(name = "joyn.storage.provider", havingValue = "local")
public class LocalCandidateResumeStorage implements CandidateResumeStorage {

    private final Path storageDirectory;

    public LocalCandidateResumeStorage(
            @Value("${joyn.media.local-directory:./media/candidate-resumes}")
            String storageDirectory) {
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    @Override
    public String store(Long candidateId, MultipartFile resume) {
        String key = "resumes/" + candidateId + "/" + UUID.randomUUID() + ".pdf";
        Path target = resolveKey(key);

        try {
            Files.createDirectories(target.getParent());
            try (var input = resume.getInputStream()) {
                Files.copy(input, target);
            }
            return key;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to store candidate resume", exception);
        }
    }

    @Override
    public String createPresignedUrl(String storageKey) {
        if (storageKey == null || !CandidateResumeStorage.STORAGE_KEY_PATTERN.matcher(storageKey).matches()) {
            throw new IllegalArgumentException("Invalid resume storage key");
        }
        String[] parts = storageKey.split("/");
        return "/api/v1/candidates/resumes/" + parts[1] + "/" + parts[2];
    }

    @Override
    public Optional<StoredCandidateResume> load(String storageKey) {
        if (storageKey == null || !CandidateResumeStorage.STORAGE_KEY_PATTERN.matcher(storageKey).matches()) {
            return Optional.empty();
        }

        Path file = resolveKey(storageKey);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }

        try {
            UrlResource resource = new UrlResource(file.toUri());
            MediaType mediaType = MediaTypeFactory.getMediaType(resource)
                    .orElse(MediaType.APPLICATION_OCTET_STREAM);
            return Optional.of(new StoredCandidateResume(resource, mediaType));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load candidate resume", exception);
        }
    }

    private Path resolveKey(String storageKey) {
        Path file = storageDirectory.resolve(storageKey).normalize();
        if (!file.startsWith(storageDirectory)) {
            throw new IllegalArgumentException("Invalid resume storage key");
        }
        return file;
    }
}
