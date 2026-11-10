package com.gpstl.joyn.config;

import com.gpstl.joyn.candidate.service.CandidateResumeStorage;
import com.gpstl.joyn.candidate.service.SupabaseCandidateResumeStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Conditional;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.Duration;

@Configuration
@Conditional(SupabaseStorageConfiguredCondition.class)
public class SupabaseStorageConfig {

    @Bean
    public S3Client s3Client(
            @Value("${joyn.storage.supabase.endpoint}") String endpoint,
            @Value("${joyn.storage.supabase.region}") String region,
            @Value("${joyn.storage.supabase.access-key}") String accessKey,
            @Value("${joyn.storage.supabase.secret-key}") String secretKey
    ) {
        return S3Client.builder()
                .endpointOverride(storageEndpoint(endpoint))
                .region(Region.of(region))
                .credentialsProvider(credentials(accessKey, secretKey))
                .serviceConfiguration(
                    S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build()
                )
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(
            @Value("${joyn.storage.supabase.endpoint}") String endpoint,
            @Value("${joyn.storage.supabase.region}") String region,
            @Value("${joyn.storage.supabase.access-key}") String accessKey,
            @Value("${joyn.storage.supabase.secret-key}") String secretKey
    ) {
        return S3Presigner.builder()
                .endpointOverride(storageEndpoint(endpoint))
                .region(Region.of(region))
                .credentialsProvider(credentials(accessKey, secretKey))
                .serviceConfiguration(
                    S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build()
                )
                .build();
    }

    @Bean
    public CandidateResumeStorage supabaseCandidateResumeStorage(
            S3Client s3Client,
            S3Presigner s3Presigner,
            @Value("${joyn.storage.supabase.bucket}") String bucket
    ) {
        return new SupabaseCandidateResumeStorage(
                s3Client,
                s3Presigner,
                bucket,
                Duration.ofMinutes(15)
        );
    }

    private static URI storageEndpoint(String endpoint) {
        URI uri = URI.create(endpoint);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalStateException("Supabase S3 endpoint must be an HTTPS URL");
        }
        return uri;
    }

    private static StaticCredentialsProvider credentials(String accessKey, String secretKey) {
        if (accessKey.isBlank() || secretKey.isBlank()) {
            throw new IllegalStateException("Supabase S3 access and secret keys must not be blank");
        }
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }
}