package com.gpstl.joyn.company.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Logos d'entreprise sur Supabase S3, dans le même bucket que les CV et les photos de profil,
 * sous company_logos/{companyId}/{uuid}.webp. Copie de SupabaseUserProfilePictureStorage (U86).
 */
public class SupabaseCompanyLogoStorage implements CompanyLogoStorage {

    private static final Logger log = LoggerFactory.getLogger(SupabaseCompanyLogoStorage.class);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final Duration presignedUrlDuration;

    public SupabaseCompanyLogoStorage(S3Client s3Client, S3Presigner s3Presigner, String bucket,
                                      Duration presignedUrlDuration) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.presignedUrlDuration = presignedUrlDuration;
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
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType("image/webp")
                        .contentDisposition("inline; filename=\"logo.webp\"")
                        .cacheControl("public, max-age=31536000, immutable")
                        .build(),
                RequestBody.fromBytes(webpBytes));
        log.info("Logo d'entreprise uploadé sur Supabase S3 : {}", key);
        return key;
    }

    @Override
    public String createPresignedUrl(String storageKey) {
        validateStorageKey(storageKey);
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey)
                .responseContentType("image/webp")
                .responseContentDisposition("inline; filename=\"logo.webp\"")
                .responseCacheControl("public, max-age=31536000, immutable")
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(presignedUrlDuration)
                .getObjectRequest(getObjectRequest)
                .build();
        return s3Presigner.presignGetObject(presignRequest).url().toExternalForm();
    }

    @Override
    public void delete(String storageKey) {
        if (storageKey == null || !STORAGE_KEY_PATTERN.matcher(storageKey.trim()).matches()) {
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(storageKey.trim()).build());
            log.info("Logo d'entreprise supprimé du bucket Supabase : {}", storageKey);
        } catch (Exception e) {
            log.warn("Impossible de supprimer le logo {} de Supabase : {}", storageKey, e.getMessage());
        }
    }

    @Override
    public Optional<StoredCompanyLogo> load(String storageKey) {
        validateStorageKey(storageKey);
        return Optional.empty(); // sur Supabase, le front utilise directement l'URL présignée
    }

    private static void validateStorageKey(String storageKey) {
        if (storageKey == null || !STORAGE_KEY_PATTERN.matcher(storageKey.trim()).matches()) {
            throw new IllegalArgumentException("Clé de stockage de logo invalide : " + storageKey);
        }
    }
}
