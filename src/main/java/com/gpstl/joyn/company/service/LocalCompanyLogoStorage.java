package com.gpstl.joyn.company.service;

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
 * Logos d'entreprise sur le disque local (développement hors ligne et tests).
 * Activé par joyn.storage.provider=local, comme LocalUserProfilePictureStorage (U86).
 * Les fichiers sont servis par LocalCompanyLogoController.
 */
@Component
@ConditionalOnProperty(name = "joyn.storage.provider", havingValue = "local")
public class LocalCompanyLogoStorage implements CompanyLogoStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalCompanyLogoStorage.class);

    private final Path storageDirectory;

    public LocalCompanyLogoStorage(@Value("${joyn.media.local-directory:./media}") String baseDirectory) {
        this.storageDirectory = Path.of(baseDirectory, "company-logos").toAbsolutePath().normalize();
    }

    @Override
    public String store(Long companyId, byte[] webpBytes) {
        if (companyId == null || companyId <= 0) {
            throw new IllegalArgumentException("L'identifiant de l'entreprise doit être un entier positif.");
        }
        if (webpBytes == null || webpBytes.length == 0) {
            throw new IllegalArgumentException("Les données de l'image ne peuvent pas être vides.");
        }
        String key = "company_logos/" + companyId + "/" + UUID.randomUUID() + ".webp";
        Path target = resolveKey(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, webpBytes);
            log.info("Logo d'entreprise stocké localement : {}", target);
            return key;
        } catch (IOException e) {
            throw new IllegalStateException("Impossible d'enregistrer le logo localement.", e);
        }
    }

    @Override
    public String createPresignedUrl(String storageKey) {
        validateStorageKey(storageKey);
        String[] parts = storageKey.trim().split("/");
        return "/api/v1/companies/logos/" + parts[1] + "/" + parts[2];
    }

    @Override
    public void delete(String storageKey) {
        if (storageKey == null || !STORAGE_KEY_PATTERN.matcher(storageKey.trim()).matches()) {
            return;
        }
        try {
            if (Files.deleteIfExists(resolveKey(storageKey.trim()))) {
                log.info("Logo d'entreprise local supprimé : {}", storageKey);
            }
        } catch (IOException e) {
            log.warn("Impossible de supprimer le logo local {} : {}", storageKey, e.getMessage());
        }
    }

    @Override
    public Optional<StoredCompanyLogo> load(String storageKey) {
        if (storageKey == null || !STORAGE_KEY_PATTERN.matcher(storageKey.trim()).matches()) {
            return Optional.empty();
        }
        Path target = resolveKey(storageKey.trim());
        if (!Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            Resource resource = new UrlResource(target.toUri());
            return Optional.of(new StoredCompanyLogo(resource, MediaType.parseMediaType("image/webp")));
        } catch (MalformedURLException e) {
            return Optional.empty();
        }
    }

    /** Empêche une clé de sortir du dossier autorisé (ex. "../../"). */
    private Path resolveKey(String key) {
        Path resolved = storageDirectory.resolve(key).normalize();
        if (!resolved.startsWith(storageDirectory)) {
            throw new IllegalArgumentException("La clé de stockage dépasse le répertoire autorisé.");
        }
        return resolved;
    }

    private static void validateStorageKey(String storageKey) {
        if (storageKey == null || !STORAGE_KEY_PATTERN.matcher(storageKey.trim()).matches()) {
            throw new IllegalArgumentException("Clé de stockage de logo invalide : " + storageKey);
        }
    }
}
