package com.gpstl.joyn.config;

import com.gpstl.joyn.candidate.service.CandidateResumeStorage;
import com.gpstl.joyn.candidate.service.UnavailableCandidateResumeStorage;
import com.gpstl.joyn.user.service.UnavailableUserProfilePictureStorage;
import com.gpstl.joyn.user.service.UserProfilePictureStorage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnavailableSupabaseStorageConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    SupabaseStorageConfig.class,
                    UnavailableSupabaseStorageConfig.class);

    @Test
    void missingSupabaseCredentialsDoNotPreventStartupAndCvCallsReturn503() {
        contextRunner
                .withPropertyValues(
                        "joyn.storage.provider=supabase",
                        "joyn.storage.supabase.endpoint=",
                        "joyn.storage.supabase.region=",
                        "joyn.storage.supabase.access-key=",
                        "joyn.storage.supabase.secret-key=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CandidateResumeStorage.class);
                    assertThat(context.getBean(CandidateResumeStorage.class))
                            .isInstanceOf(UnavailableCandidateResumeStorage.class);
                    assertThatThrownBy(() -> context.getBean(CandidateResumeStorage.class)
                            .createPresignedUrl("resumes/42/123e4567-e89b-12d3-a456-426614174000.pdf"))
                            .isInstanceOf(ResponseStatusException.class)
                            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                            .isEqualTo(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);

                    assertThat(context).hasSingleBean(UserProfilePictureStorage.class);
                    assertThat(context.getBean(UserProfilePictureStorage.class))
                            .isInstanceOf(UnavailableUserProfilePictureStorage.class);
                    assertThatThrownBy(() -> context.getBean(UserProfilePictureStorage.class)
                            .createPresignedUrl("profile_pictures/42/123e4567-e89b-12d3-a456-426614174000.webp"))
                            .isInstanceOf(ResponseStatusException.class)
                            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                            .isEqualTo(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
                });
    }

    @Test
    void localProviderDoesNotCreateSupabaseStorageBeans() {
        contextRunner
                .withPropertyValues("joyn.storage.provider=local")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(CandidateResumeStorage.class);
                    assertThat(context).doesNotHaveBean(UserProfilePictureStorage.class);
                    assertThat(context).doesNotHaveBean("s3Client");
                });
    }
}
