package com.gpstl.joyn.company.service;

import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Stockage des logos d'entreprise (R84). Même principe que UserProfilePictureStorage (U86) :
 * <ul>
 *   <li>en base, companies.logo_url contient la CLÉ de stockage, ex. company_logos/12/uuid.webp ;</li>
 *   <li>au moment de répondre au front, la clé est transformée en URL utilisable (createPresignedUrlIfValid).</li>
 * </ul>
 * Trois adaptateurs : Supabase S3 (production), local (développement et tests), indisponible (non configuré).
 */
public interface CompanyLogoStorage {

    Pattern STORAGE_KEY_PATTERN = Pattern.compile(
            "^company_logos/[1-9][0-9]*/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.webp$");

    /**
     * Enregistre le logo (déjà converti en WebP) de l'entreprise.
     *
     * @return la clé de stockage, ex. company_logos/12/uuid.webp
     */
    String store(Long companyId, byte[] webpBytes);

    /** URL (présignée ou locale) pour afficher le logo. */
    String createPresignedUrl(String storageKey);

    /** Supprime le fichier ; ne fait rien si la clé n'est pas une clé de logo Joyn. */
    void delete(String storageKey);

    /** Charge le fichier (utilisé en mode local pour le servir). */
    Optional<StoredCompanyLogo> load(String storageKey);

    /**
     * Transforme ce qui est enregistré dans companies.logo_url en URL pour le front :
     * <ul>
     *   <li>vide ou null : null (pas de logo) ;</li>
     *   <li>déjà une URL http(s) (logo saisi à la main avant R84) : renvoyée telle quelle ;</li>
     *   <li>clé de logo Joyn : URL d'accès ;</li>
     *   <li>autre chose : null, avec un avertissement dans les logs.</li>
     * </ul>
     */
    default String createPresignedUrlIfValid(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return null;
        }
        if (storageKey.startsWith("http://") || storageKey.startsWith("https://")) {
            return storageKey;
        }
        if (!STORAGE_KEY_PATTERN.matcher(storageKey.trim()).matches()) {
            LoggerFactory.getLogger(CompanyLogoStorage.class)
                    .warn("Clé de stockage de logo d'entreprise non reconnue ignorée : {}", storageKey);
            return null;
        }
        return createPresignedUrl(storageKey.trim());
    }

    record StoredCompanyLogo(Resource resource, MediaType mediaType) {
    }
}
