package com.gpstl.joyn.user.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupabaseUserProfilePictureStorageTest {

    @Test
    @DisplayName("Stores WebP in profile_pictures/{userId}/{uuid}.webp and generates presigned URL")
    void storesWebpInUserDirectoryAndCreatesPresignedUrl() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        try (S3Presigner presigner = S3Presigner.builder()
                .endpointOverride(URI.create("https://storage.example.test/storage/v1/s3"))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-access", "test-secret")))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()) {

            SupabaseUserProfilePictureStorage storage = new SupabaseUserProfilePictureStorage(
                    s3Client, presigner, "joyn_file_bucket", Duration.ofHours(24));

            byte[] dummyWebp = "RIFF....WEBP".getBytes();
            String key = storage.store(42L, dummyWebp);

            assertThat(key).matches("profile_pictures/42/[0-9a-f-]{36}\\.webp");

            ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));

            PutObjectRequest putRequest = requestCaptor.getValue();
            assertThat(putRequest.bucket()).isEqualTo("joyn_file_bucket");
            assertThat(putRequest.key()).isEqualTo(key);
            assertThat(putRequest.contentType()).isEqualTo("image/webp");

            String url = storage.createPresignedUrl(key);
            assertThat(url).contains("joyn_file_bucket", key, "X-Amz-Expires=86400");

            // Test delete
            storage.delete(key);
            ArgumentCaptor<DeleteObjectRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
            verify(s3Client).deleteObject(deleteCaptor.capture());
            assertThat(deleteCaptor.getValue().bucket()).isEqualTo("joyn_file_bucket");
            assertThat(deleteCaptor.getValue().key()).isEqualTo(key);

            // Test createPresignedUrlIfValid with external URL
            assertThat(storage.createPresignedUrlIfValid("https://example.com/avatar.jpg"))
                    .isEqualTo("https://example.com/avatar.jpg");

            // Test createPresignedUrlIfValid with invalid key
            assertThat(storage.createPresignedUrlIfValid("invalid/key.png")).isNull();
            assertThat(storage.createPresignedUrlIfValid(null)).isNull();

            // Test invalid key for createPresignedUrl
            assertThatThrownBy(() -> storage.createPresignedUrl("../other.webp"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
