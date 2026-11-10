package com.gpstl.joyn.candidate.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalCandidateResumeStorageTest {

    @TempDir
    Path storageDirectory;

    @Test
    void storesAndLoadsResumeUsingGeneratedStorageKey() throws IOException {
        byte[] content = "%PDF-1.7\nresume data".getBytes();
        LocalCandidateResumeStorage storage =
                new LocalCandidateResumeStorage(storageDirectory.toString());
        MockMultipartFile upload = new MockMultipartFile(
                "resume", "candidate.pdf", "application/pdf", content);

        String key = storage.store(42L, upload);
        CandidateResumeStorage.StoredCandidateResume stored =
                storage.load(key).orElseThrow();

        assertTrue(key.matches("resumes/42/[0-9a-f-]{36}\\.pdf"));
        try (var input = stored.resource().getInputStream()) {
            assertArrayEquals(content, input.readAllBytes());
        }
        assertEquals("application/pdf", stored.mediaType().toString());
    }

    @Test
    void doesNotLoadFilesOutsideStorageDirectory() {
        LocalCandidateResumeStorage storage =
                new LocalCandidateResumeStorage(storageDirectory.toString());

        assertFalse(storage.load("../../outside.pdf").isPresent());
        assertFalse(Files.exists(storageDirectory.resolve("outside.pdf")));
    }
}
