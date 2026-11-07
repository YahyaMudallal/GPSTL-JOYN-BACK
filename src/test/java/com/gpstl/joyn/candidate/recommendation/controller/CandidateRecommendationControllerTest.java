package com.gpstl.joyn.candidate.recommendation.controller;

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
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CandidateRecommendationControllerTest extends JobDatabaseTestSupport {

    private static final String SCHEMA =
            "job_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private static final String ROUTE = "/api/v1/candidates/recommendations";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        configureDatabase(registry, SCHEMA);
    }

    @Value("${joyn.security.jwt.secret}")
    String secret;

    private MockMvc mvc;
    private long candidateProfile;

    @BeforeEach
    void setUpRecommendations() {
        mvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
        candidateProfile = insert("""
                INSERT INTO candidate_profiles(
                    user_id, first_name, last_name, location, years_of_experience, available_from,
                    latitude, longitude)
                VALUES (?, 'Candidate', 'Test', 'Paris', 1, ?, 48.859, 2.347) RETURNING id
                """, candidate, LocalDate.now().plusDays(31));
        jdbc.update("UPDATE job_offers SET latitude = 48.859, longitude = 2.347 WHERE id = ?", job);
    }

    private String token(long userId, String role) {
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(SecurityConfig.secretKey(secret)));
        var claims = JwtClaimsSet.builder().subject(Long.toString(userId)).claim("role", role)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    @Test
    void requiresCandidateJwtRole() throws Exception {
        mvc.perform(get(ROUTE)).andExpect(status().isUnauthorized());
        mvc.perform(get(ROUTE).header("Authorization", token(recruiter, "RECRUITER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsWeightedBreakdownAndPersistsScore() throws Exception {
        jdbc.update("INSERT INTO candidate_skills(candidate_id, skill_id, level) VALUES (?, ?, 'ADVANCED')",
                candidateProfile, skill);
        long secondSkill = insert("INSERT INTO skills(name) VALUES (?) RETURNING id", "Second-" + UUID.randomUUID());
        jdbc.update("""
                INSERT INTO job_offer_skills(job_offer_id, skill_id, required_level)
                VALUES (?, ?, 'BASIC')
                """, job, secondSkill);
        jdbc.update("""
                INSERT INTO candidate_educations(
                    candidate_id, school_name, field_of_study, degree, postbac_year)
                VALUES (?, 'University', 'Engineering', 'BACHELORS', 'BAC_2')
                """, candidateProfile);
        jdbc.update("""
                UPDATE job_offers
                SET location = 'Paris', start_date = ?, degree_level_required = 'BAC_3',
                    experience_level_required = 'MID', latitude = 48.859, longitude = 2.347
                WHERE id = ?
                """, LocalDate.now(), job);

        mvc.perform(get(ROUTE).header("Authorization", token(candidate, "CANDIDATE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].offer.id").value(job))
                .andExpect(jsonPath("$.content[0].match_score").value(54))
                .andExpect(jsonPath("$.content[0].score_breakdown.skills").value(50))
                .andExpect(jsonPath("$.content[0].score_breakdown.experience").value(33))
                .andExpect(jsonPath("$.content[0].score_breakdown.education").value(66))
                .andExpect(jsonPath("$.content[0].score_breakdown.location").value(100))
                .andExpect(jsonPath("$.content[0].score_breakdown.distance_km").value(0.0))
                .andExpect(jsonPath("$.content[0].score_breakdown.availability").value(50))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.limit").value(20));

        assertThat(jdbc.queryForObject("""
                SELECT match_score FROM candidate_recommendation_scores
                WHERE candidate_id = ? AND job_offer_id = ?
                """, Short.class, candidateProfile, job)).isEqualTo((short) 54);
    }

    @Test
    void excludesSwipedAndAppliedOffersAndAppliesFiltersAndPagination() throws Exception {
        long swipedJob = job;
        long appliedJob = createOffer("Applied", "ONSITE", null, "Paris");
        long matchingJob = createOffer("Remote internship", "REMOTE", "3/2", "Paris");
        long secondMatchingJob = createOffer("Another remote internship", "REMOTE", "3/2", "Paris");
        jdbc.update("INSERT INTO job_swipes(candidate_id, job_offer_id, action) VALUES (?, ?, 'PASS')",
                candidateProfile, swipedJob);
        int pending = jdbc.queryForObject(
                "SELECT id FROM application_statuses WHERE code = 'PENDING'", Integer.class);
        jdbc.update("INSERT INTO applications(candidate_id, job_offer_id, status_id) VALUES (?, ?, ?)",
                candidateProfile, appliedJob, pending);

        mvc.perform(get(ROUTE)
                        .queryParam("contract_type", "CDI")
                        .queryParam("rhythm_type", "3/2")
                        .queryParam("remote", "REMOTE")
                        .queryParam("location", "Paris")
                        .queryParam("limit", "1")
                        .header("Authorization", token(candidate, "CANDIDATE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].offer.id").value(secondMatchingJob))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.hasNext").value(true));

        mvc.perform(get(ROUTE)
                        .queryParam("contract_type", "CDI")
                        .queryParam("rhythm_type", "3/2")
                        .queryParam("remote", "REMOTE")
                        .queryParam("location", "Paris")
                        .queryParam("limit", "1")
                        .queryParam("page", "2")
                        .header("Authorization", token(candidate, "CANDIDATE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].offer.id").value(matchingJob))
                .andExpect(jsonPath("$.hasNext").value(false));

        mvc.perform(get(ROUTE).header("Authorization", token(candidate, "CANDIDATE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].offer.id")
                        .value(containsInAnyOrder((int) matchingJob, (int) secondMatchingJob)));
    }

    @Test
    void maxDistanceUsesCoordinatesAndKeepsRemoteOffers() throws Exception {
        long nearbyJob = insert("""
                INSERT INTO job_offers(
                    company_id, recruiter_id, contract_type_id, title, description, location,
                    latitude, longitude, status, published_at)
                VALUES (?, ?, ?, 'Nearby', 'Description', 'Versailles', 48.8014, 2.1301,
                    'PUBLISHED', NOW()) RETURNING id
                """, company, profile, contract);
        long distantJob = insert("""
                INSERT INTO job_offers(
                    company_id, recruiter_id, contract_type_id, title, description, location,
                    latitude, longitude, status, published_at)
                VALUES (?, ?, ?, 'Distant', 'Description', 'Lyon', 45.764, 4.8357,
                    'PUBLISHED', NOW()) RETURNING id
                """, company, profile, contract);

        mvc.perform(get(ROUTE)
                        .queryParam("max_distance_km", "20")
                        .header("Authorization", token(candidate, "CANDIDATE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].offer.id")
                        .value(containsInAnyOrder((int) nearbyJob, (int) job)))
                .andExpect(jsonPath("$.totalElements").value(2));

        Double distance = jdbc.queryForObject("""
                SELECT distance_km FROM candidate_recommendation_scores
                WHERE candidate_id = ? AND job_offer_id = ?
                """, Double.class, candidateProfile, nearbyJob);
        assertThat(distance).isBetween(17.0, 18.0);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM candidate_recommendation_scores
                WHERE candidate_id = ? AND job_offer_id = ?
                """, Integer.class, candidateProfile, distantJob)).isZero();

        mvc.perform(get(ROUTE)
                        .queryParam("unknown", "value")
                        .header("Authorization", token(candidate, "CANDIDATE")))
                .andExpect(status().isBadRequest());
    }

    private long createOffer(String title, String remoteMode, String rhythmType, String location) {
        return insert("""
                INSERT INTO job_offers(
                    company_id, recruiter_id, contract_type_id, title, description, location,
                    remote_mode, status, rhythm_type, published_at)
                VALUES (?, ?, ?, ?, 'Description', ?, ?, 'PUBLISHED', ?, NOW()) RETURNING id
                """, company, profile, contract, title, location, remoteMode, rhythmType);
    }
}
