package com.gpstl.joyn.candidate.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Local development adapter until the deployment's media server is selected.
 */
@Component
public class LocalCandidateResumeStorage implements CandidateResumeStorage {

    private static final Pattern SAFE_STORAGE_KEY =
            Pattern.compile("[0-9a-fA-F-]{36}(?:\\.[a-z0-9]{1,10})?");

    private final Path storageDirectory;

    public LocalCandidateResumeStorage(
            @Value("${joyn.media.local-directory:./media/candidate-resumes}")
            String storageDirectory) {
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    @Override
    public String store(MultipartFile resume) {
        String key = UUID.randomUUID() + extensionFrom(resume.getOriginalFilename());
        Path target = resolveKey(key);

        try {
            Files.createDirectories(storageDirectory);
            try (var input = resume.getInputStream()) {
                Files.copy(input, target);
            }
            return key;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to store candidate resume", exception);
        }
    }

    @Override
    public Optional<StoredCandidateResume> load(String storageKey) {
        if (storageKey == null || !SAFE_STORAGE_KEY.matcher(storageKey).matches()) {
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

    private String extensionFrom(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }

        int extensionIndex = originalFilename.lastIndexOf('.');
        if (extensionIndex < 0 || extensionIndex == originalFilename.length() - 1) {
            return "";
        }

        String extension = originalFilename.substring(extensionIndex + 1)
                .toLowerCase(Locale.ROOT);
        return extension.matches("[a-z0-9]{1,10}") ? "." + extension : "";
    }
}
