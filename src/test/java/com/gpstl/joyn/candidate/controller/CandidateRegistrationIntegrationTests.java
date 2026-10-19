package com.gpstl.joyn.candidate.controller;

import com.gpstl.joyn.JoynApplication;
import com.gpstl.joyn.candidate.repository.CandidateRepository;
import com.gpstl.joyn.common.repository.UserRepository;
import com.gpstl.joyn.joboffer.JobDatabaseTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = JoynApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@AutoConfigureTestRestTemplate
class CandidateRegistrationIntegrationTests extends JobDatabaseTestSupport {

    private static final String SCHEMA = "job_test_" +
            UUID.randomUUID().toString().replace("-", "").substring(0, 12);

    private static final Path MEDIA_DIRECTORY =
            Path.of("target", "test-media", "candidate-" + UUID.randomUUID())
                    .toAbsolutePath()
                    .normalize();

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        configureDatabase(registry, SCHEMA);
        registry.add("joyn.media.local-directory", MEDIA_DIRECTORY::toString);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestRestTemplate rest;

    @Test
    void shouldRegisterCandidateAndServeUploadedResume() {
        String email = "candidate-" + UUID.randomUUID() + "@example.com";
        byte[] resume = "Candidate resume contents".getBytes();

        MultiValueMap<String, Object> body = registrationForm(email);
        body.add("cv", new ByteArrayResource(resume) {
            @Override
            public String getFilename() {
                return "resume.txt";
            }
        });

        ResponseEntity<Map> response = register(body);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        Map<?, ?> result = response.getBody();
        assertNotNull(result);
        assertNotNull(result.get("profileId"));
        assertNotNull(result.get("userId"));
        assertEquals(email, result.get("email"));
        assertNotNull(result.get("resumeUrl"));
        assertTrue(userRepository.findByEmailIgnoreCase(email)
                .orElseThrow().getPasswordHash().startsWith("$2"));
        assertEquals("CANDIDATE", userRepository.findByEmailIgnoreCase(email)
                .orElseThrow().getRole());
        assertEquals(result.get("resumeUrl"),
                candidateRepository.findByUserId(((Number) result.get("userId")).longValue())
                        .orElseThrow().getResumeUrl());

        ResponseEntity<byte[]> download = rest.getForEntity(
                "http://localhost:" + port + result.get("resumeUrl"),
                byte[].class);
        assertEquals(HttpStatus.OK, download.getStatusCode());
        assertArrayEquals(resume, download.getBody());
    }

    @Test
    void shouldRegisterCandidateWithoutResume() {
        String email = "candidate-" + UUID.randomUUID() + "@example.com";

        ResponseEntity<Map> response = register(registrationForm(email));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNull(response.getBody().get("resumeUrl"));
        assertTrue(userRepository.existsByEmailIgnoreCase(email));
    }

    @AfterAll
    static void removeTestMedia() throws IOException {
        if (!Files.exists(MEDIA_DIRECTORY)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(MEDIA_DIRECTORY)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private MultiValueMap<String, Object> registrationForm(String email) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("firstName", "John");
        body.add("lastName", "Smith");
        body.add("email", email);
        body.add("password", "SecurePassword123!");
        return body;
    }

    private ResponseEntity<Map> register(MultiValueMap<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.postForEntity(
                "http://localhost:" + port + "/api/v1/candidates/register",
                new HttpEntity<>(body, headers),
                Map.class);
    }
}
