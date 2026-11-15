package com.gpstl.joyn.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptateur de stockage local pour l'environnement de développement hors ligne.
 */
@Component
@ConditionalOnProperty(name = "joyn.storage.provider", havingValue = "local")
public class LocalUserProfilePictureStorage implements UserProfilePictureStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalUserProfilePictureStorage.class);

    private final Path storageDirectory;

    public LocalUserProfilePictureStorage(
            @Value("${joyn.media.local-directory:./media}") String baseDirectory) {
        this.storageDirectory = Path.of(baseDirectory, "profile-pictures").toAbsolutePath().normalize();
    }

    @Override
    public String store(Long userId, byte[] webpBytes) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("L'identifiant utilisateur doit être un entier positif.");
        }
        if (webpBytes == null || webpBytes.length == 0) {
            throw new IllegalArgumentException("Les données de l'image ne peuvent pas être vides.");
        }

        String key = "profile_pictures/" + userId + "/" + UUID.randomUUID() + ".webp";
        Path target = resolveKey(key);

        try {
            Files.createDirectories(target.getParent());
            Files.write(target, webpBytes);
            log.info("Photo de profil stockée localement : {}", target);
            return key;
        } catch (IOException e) {
            throw new IllegalStateException("Impossible d'enregistrer l'image de profil localement.", e);
        }
    }

    @Override
    public String createPresignedUrl(String storageKey) {
        validateStorageKey(storageKey);
        String[] parts = storageKey.split("/");
        return "/api/v1/users/profile-pictures/" + parts[1] + "/" + parts[2];
    }

    @Override
    public void delete(String storageKey) {
        if (storageKey == null || !UserProfilePictureStorage.STORAGE_KEY_PATTERN.matcher(storageKey.trim()).matches()) {
            return;
        }
        try {
            Path target = resolveKey(storageKey.trim());
            boolean deleted = Files.deleteIfExists(target);
            if (deleted) {
                log.info("Photo de profil locale supprimée : {}", target);
            }
        } catch (IOException e) {
            log.warn("Impossible de supprimer le fichier local {}: {}", storageKey, e.getMessage());
        }
    }

    @Override
    public Optional<StoredProfilePicture> load(String storageKey) {
        if (storageKey == null || !UserProfilePictureStorage.STORAGE_KEY_PATTERN.matcher(storageKey.trim()).matches()) {
            return Optional.empty();
        }
        Path target = resolveKey(storageKey.trim());
        if (!Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            Resource resource = new UrlResource(target.toUri());
            return Optional.of(new StoredProfilePicture(resource, MediaType.parseMediaType("image/webp")));
        } catch (MalformedURLException e) {
            return Optional.empty();
        }
    }

    private Path resolveKey(String key) {
        Path resolved = storageDirectory.resolve(key).normalize();
        if (!resolved.startsWith(storageDirectory)) {
            throw new IllegalArgumentException("La clé de stockage dépasse le répertoire autorisé.");
        }
        return resolved;
    }

    private static void validateStorageKey(String storageKey) {
        if (storageKey == null || !UserProfilePictureStorage.STORAGE_KEY_PATTERN.matcher(storageKey.trim()).matches()) {
            throw new IllegalArgumentException("Clé de stockage de photo de profil invalide : " + storageKey);
        }
    }
}
