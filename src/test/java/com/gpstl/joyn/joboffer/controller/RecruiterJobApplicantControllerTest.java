package com.gpstl.joyn.joboffer.controller;

import com.gpstl.joyn.config.SecurityConfig;
import com.gpstl.joyn.joboffer.JobDatabaseTestSupport;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecruiterJobApplicantControllerTest extends JobDatabaseTestSupport {

    private static final String SCHEMA =
            "job_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        configureDatabase(registry, SCHEMA);
    }

    @Value("${joyn.security.jwt.secret}")
    String secret;

    private MockMvc mvc;
    private long firstCandidate;
    private long secondCandidate;
    private long candidateWithoutScore;
    private long hiddenCandidate;
    private long otherCompanyJob;

    @BeforeEach
    void setUpApplicants() {
        mvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
        firstCandidate = createCandidate("Ada", "Lovelace", "Paris", true);
        secondCandidate = createCandidate("Grace", "Hopper", "Lyon", true);
        candidateWithoutScore = createCandidate("Linus", "Torvalds", "Paris", true);
        hiddenCandidate = createCandidate("Hidden", "Profile", "Paris", false);

        apply(firstCandidate);
        apply(secondCandidate);
        apply(candidateWithoutScore);
        apply(hiddenCandidate);
        storeScore(firstCandidate, 85, 100, 80, 60, 100, 75, 12.5);
        storeScore(secondCandidate, 72, 50, 80, 60, 80, 75, 80.0);
        storeScore(hiddenCandidate, 99, 100, 100, 100, 100, 100, 1.0);

        otherCompanyJob = insert("""
                INSERT INTO job_offers(
                    company_id, recruiter_id, contract_type_id, title, description, status)
                VALUES ((SELECT id FROM companies WHERE id <> ? LIMIT 1),
                        (SELECT id FROM recruiter_profiles WHERE company_id <> ? LIMIT 1),
                        ?, 'Other company offer', 'Description', 'PUBLISHED')
                RETURNING id
                """, company, company, contract);
    }

    @Test
    void requiresRecruiterAuthenticationAndHidesOffersOwnedByOtherCompanies() throws Exception {
        mvc.perform(get(route(job)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(route(job)).header("Authorization", token(candidate, "CANDIDATE")))
                .andExpect(status().isForbidden());
        mvc.perform(get(route(job)).header("Authorization", token(outsider, "RECRUITER")))
                .andExpect(status().isForbidden());
        mvc.perform(get(route(9_999_999L)).header("Authorization", token(recruiter, "RECRUITER")))
                .andExpect(status().isNotFound());
        mvc.perform(get(route(otherCompanyJob)).header("Authorization", token(recruiter, "RECRUITER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsOnlyVisibleApplicantsOrderedByStoredScoreAndPaginates() throws Exception {
        mvc.perform(get(route(job))
                        .queryParam("limit", "1")
                        .header("Authorization", token(recruiter, "RECRUITER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].candidate.first_name").value("Ada"))
                .andExpect(jsonPath("$.content[0].match_score").value(85))
                .andExpect(jsonPath("$.content[0].score_breakdown.skills").value(100))
                .andExpect(jsonPath("$.content[0].score_breakdown.distance_km").value(12.5))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.hasNext").value(true));

        mvc.perform(get(route(job))
                        .queryParam("page", "2")
                        .queryParam("limit", "1")
                        .header("Authorization", token(recruiter, "RECRUITER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].candidate.first_name").value("Grace"))
                .andExpect(jsonPath("$.content[0].match_score").value(72));

        mvc.perform(get(route(job))
                        .queryParam("page", "3")
                        .queryParam("limit", "1")
                        .header("Authorization", token(recruiter, "RECRUITER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].candidate.first_name").value("Linus"))
                .andExpect(jsonPath("$.content[0].match_score").doesNotExist())
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void rejectsFilteringQueryParameters() throws Exception {
        mvc.perform(get(route(job))
                        .queryParam("min_score", "80")
                        .header("Authorization", token(recruiter, "RECRUITER")))
                .andExpect(status().isBadRequest());
        mvc.perform(get(route(job))
                        .queryParam("location", "Paris")
                        .header("Authorization", token(recruiter, "RECRUITER")))
                .andExpect(status().isBadRequest());
        mvc.perform(get(route(job))
                        .queryParam("skills", "Java")
                        .header("Authorization", token(recruiter, "RECRUITER")))
                .andExpect(status().isBadRequest());
        mvc.perform(get(route(job))
                        .queryParam("max_distance_km", "20")
                        .header("Authorization", token(recruiter, "RECRUITER")))
                .andExpect(status().isBadRequest());
    }

    private long createCandidate(String firstName, String lastName, String location, boolean visible) {
        long userId = insert("""
                INSERT INTO users(email, password_hash, role)
                VALUES (?, 'test-only', 'CANDIDATE') RETURNING id
                """, UUID.randomUUID() + "@example.test");
        return insert("""
                INSERT INTO candidate_profiles(
                    user_id, first_name, last_name, location, is_visible_to_recruiters)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """, userId, firstName, lastName, location, visible);
    }

    private void apply(long candidateId) {
        int pending = jdbc.queryForObject(
                "SELECT id FROM application_statuses WHERE code = 'PENDING'", Integer.class);
        jdbc.update("""
                INSERT INTO applications(candidate_id, job_offer_id, status_id)
                VALUES (?, ?, ?)
                """, candidateId, job, pending);
    }

    private void storeScore(
            long candidateId,
            int score,
            int skillScore,
            int experience,
            int education,
            int location,
            int availability,
            double distance) {
        jdbc.update("""
                INSERT INTO candidate_recommendation_scores(
                    candidate_id, job_offer_id, match_score, skill_score, experience_score,
                    education_score, location_score, availability_score, distance_km)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, candidateId, job, score, skillScore, experience, education, location, availability, distance);
    }

    private String route(long jobId) {
        return "/api/v1/recruiters/jobs/" + jobId + "/recommendations";
    }

    private String token(long userId, String role) {
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(SecurityConfig.secretKey(secret)));
        var claims = JwtClaimsSet.builder().subject(Long.toString(userId)).claim("role", role)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
