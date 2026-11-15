package com.gpstl.joyn.user.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/**
 * Implémentation de repli lorsque le stockage Supabase n'est pas configuré
 * et que le fournisseur local n'est pas activé.
 */
public class UnavailableUserProfilePictureStorage implements UserProfilePictureStorage {

    private static final String MESSAGE =
            "Le stockage des photos de profil est indisponible : configurez les variables d'environnement Supabase S3 "
                    + "ou activez le fournisseur de stockage local (joyn.storage.provider=local).";

    @Override
    public String store(Long userId, byte[] webpBytes) {
        throw unavailable();
    }

    @Override
    public String createPresignedUrl(String storageKey) {
        throw unavailable();
    }

    @Override
    public void delete(String storageKey) {
        // Opération silencieuse lorsque le stockage est indisponible
    }

    @Override
    public Optional<StoredProfilePicture> load(String storageKey) {
        throw unavailable();
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, MESSAGE);
    }
}
