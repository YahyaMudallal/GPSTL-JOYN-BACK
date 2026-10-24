package com.gpstl.joyn.candidate.repository;

import com.gpstl.joyn.candidate.model.Candidate;
import com.gpstl.joyn.candidate.repository.CandidateRepository;
import com.gpstl.joyn.common.model.User;
import com.gpstl.joyn.common.repository.UserRepository;
import com.gpstl.joyn.joboffer.JobDatabaseTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CandidateRepositoryTests extends JobDatabaseTestSupport {

    private static final String SCHEMA = "job_test_" +
            UUID.randomUUID().toString().replace("-", "").substring(0, 12);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        configureDatabase(registry, SCHEMA);
    }

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldSaveAndFindCandidateProfileForUser() {
        String email = "candidate-" + UUID.randomUUID() + "@example.com";
        User user = userRepository.saveAndFlush(new User(email, "hashed-password", "CANDIDATE"));
        Candidate candidate = new Candidate(user, "John", "Smith", null);

        Candidate saved = candidateRepository.saveAndFlush(candidate);

        assertNotNull(saved.getId());
        assertTrue(candidateRepository.existsByUserEmailIgnoreCase(email));

        Candidate found = candidateRepository.findByUserId(user.getId()).orElseThrow();
        assertEquals("John", found.getFirstName());
        assertEquals("Smith", found.getLastName());
        assertEquals(email, found.getUser().getEmail());
    }
}
