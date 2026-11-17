package com.gpstl.joyn.company.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/**
 * Utilisé quand Supabase n'est pas configuré et que le stockage local n'est pas activé :
 * l'envoi d'un logo répond 503 avec un message clair, au lieu de faire planter l'application.
 */
public class UnavailableCompanyLogoStorage implements CompanyLogoStorage {

    private static final String MESSAGE =
            "Le stockage des logos d'entreprise est indisponible : configurez les variables d'environnement "
                    + "Supabase S3 ou activez le fournisseur de stockage local (joyn.storage.provider=local).";

    @Override
    public String store(Long companyId, byte[] webpBytes) {
        throw unavailable();
    }

    @Override
    public String createPresignedUrl(String storageKey) {
        throw unavailable();
    }

    @Override
    public void delete(String storageKey) {
        // rien à supprimer quand le stockage est indisponible
    }

    @Override
    public Optional<StoredCompanyLogo> load(String storageKey) {
        throw unavailable();
    }

    /**
     * Sans stockage, une clé de logo ne peut pas devenir une URL : on renvoie null (pas de logo)
     * au lieu de faire échouer toute la réponse (profil d'entreprise, messagerie, dashboard...).
     */
    @Override
    public String createPresignedUrlIfValid(String storageKey) {
        if (storageKey != null && (storageKey.startsWith("http://") || storageKey.startsWith("https://"))) {
            return storageKey;
        }
        return null;
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, MESSAGE);
    }
}
