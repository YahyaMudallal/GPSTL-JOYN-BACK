package com.gpstl.joyn.user.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalUserProfilePictureStorageTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Stores file locally, provides local URL, loads and deletes it")
    void testLocalStorageLifecycle() throws IOException {
        LocalUserProfilePictureStorage storage = new LocalUserProfilePictureStorage(tempDir.toString());

        byte[] sampleWebp = "RIFF1234WEBP".getBytes();
        String key = storage.store(7L, sampleWebp);

        assertThat(key).matches("profile_pictures/7/[0-9a-f-]{36}\\.webp");

        String url = storage.createPresignedUrl(key);
        assertThat(url).startsWith("/api/v1/users/profile-pictures/7/");

        var loaded = storage.load(key);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().mediaType().toString()).isEqualTo("image/webp");
        assertThat(loaded.get().resource().getContentAsByteArray()).isEqualTo(sampleWebp);

        storage.delete(key);
        assertThat(storage.load(key)).isEmpty();
    }

    @Test
    @DisplayName("Prevents path traversal attempts")
    void testPathTraversalProtection() {
        LocalUserProfilePictureStorage storage = new LocalUserProfilePictureStorage(tempDir.toString());

        assertThatThrownBy(() -> storage.createPresignedUrl("profile_pictures/../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
