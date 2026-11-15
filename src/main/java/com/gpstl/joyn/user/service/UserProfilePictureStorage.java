package com.gpstl.joyn.user.service;

import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Interface de stockage pour les photos de profil utilisateur.
 * <p>
 * Structure des clés : {@code profile_pictures/{userId}/{uuid}.webp}.
 * Les adaptateurs implémentent la persistance sur Supabase S3 ou le système de fichiers local.
 */
public interface UserProfilePictureStorage {

    Pattern STORAGE_KEY_PATTERN = Pattern.compile(
            "^profile_pictures/[1-9][0-9]*/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.webp$");

    /**
     * Stocke les octets WebP de l'image de profil pour l'utilisateur donné.
     *
     * @param userId    identifiant de l'utilisateur (users.id)
     * @param webpBytes contenu optimisé au format WebP
     * @return la clé de stockage relative (ex. profile_pictures/42/uuid.webp)
     */
    String store(Long userId, byte[] webpBytes);

    /**
     * Génère une URL présignée ou absolue pour accéder à l'image stockée.
     *
     * @param storageKey clé valide
     * @return URL d'accès
     */
    String createPresignedUrl(String storageKey);

    /**
     * Supprime physiquement l'image de profil du stockage.
     *
     * @param storageKey clé de stockage de l'image à retirer
     */
    void delete(String storageKey);

    /**
     * Charge la ressource stockée (utilisé notamment en mode local).
     *
     * @param storageKey clé de stockage
     * @return ressource encapsulée ou vide si non trouvée
     */
    Optional<StoredProfilePicture> load(String storageKey);

    /**
     * Génère une URL exploitable pour les réponses API :
     * <ul>
     *   <li>Si la clé est nulle ou vide : renvoie {@code null}.</li>
     *   <li>Si la clé est déjà une URL complète (http:// ou https://) : la renvoie directement.</li>
     *   <li>Si la clé correspond au format de stockage Joyn : génère l'URL présignée / d'accès.</li>
     *   <li>Sinon : trace un avertissement et renvoie {@code null}.</li>
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
            LoggerFactory.getLogger(UserProfilePictureStorage.class)
                    .warn("Clé de stockage de photo de profil non reconnue ignorée : {}", storageKey);
            return null;
        }
        return createPresignedUrl(storageKey.trim());
    }

    record StoredProfilePicture(Resource resource, MediaType mediaType) {}
}
