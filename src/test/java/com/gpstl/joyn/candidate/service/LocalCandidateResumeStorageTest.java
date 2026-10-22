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
        byte[] content = "resume data".getBytes();
        LocalCandidateResumeStorage storage =
                new LocalCandidateResumeStorage(storageDirectory.toString());
        MockMultipartFile upload = new MockMultipartFile(
                "resume", "candidate.pdf", "application/pdf", content);

        String key = storage.store(upload);
        CandidateResumeStorage.StoredCandidateResume stored =
                storage.load(key).orElseThrow();

        assertTrue(key.endsWith(".pdf"));
        assertArrayEquals(content, stored.resource().getInputStream().readAllBytes());
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
