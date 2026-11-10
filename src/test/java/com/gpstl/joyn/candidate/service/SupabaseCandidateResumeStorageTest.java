package com.gpstl.joyn.candidate.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
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
import org.mockito.ArgumentCaptor;

class SupabaseCandidateResumeStorageTest {

    @Test
    void storesPdfInCandidateDirectoryAndCreatesShortLivedPresignedUrl() {
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
            SupabaseCandidateResumeStorage storage = new SupabaseCandidateResumeStorage(
                    s3Client, presigner, "joyn_file_bucket", Duration.ofMinutes(15));
            MockMultipartFile file = new MockMultipartFile(
                    "resume", "original-name.pdf", "application/pdf", "%PDF-1.7\nbody".getBytes());

            String key = storage.store(42L, file);

            assertThat(key).matches("resumes/42/[0-9a-f-]{36}\\.pdf");
            ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(s3Client).putObject(request.capture(), any(RequestBody.class));
            assertThat(request.getValue().bucket()).isEqualTo("joyn_file_bucket");
            assertThat(request.getValue().key()).isEqualTo(key);
            assertThat(request.getValue().contentType()).isEqualTo("application/pdf");
            String url = storage.createPresignedUrl(key);
            assertThat(url).contains("joyn_file_bucket", key, "X-Amz-Expires=900");
            assertThatThrownBy(() -> storage.createPresignedUrl("../other.pdf"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
