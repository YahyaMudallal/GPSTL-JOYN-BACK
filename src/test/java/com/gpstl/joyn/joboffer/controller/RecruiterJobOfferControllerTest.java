package com.gpstl.joyn.joboffer.controller;

import com.gpstl.joyn.config.SecurityConfig;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'intégration de POST /api/v1/recruiters/jobs (R58).
 * Ils utilisent la vraie base PostgreSQL (comme JoynApplicationTests) et de vrais tokens JWT signés.
 */
@SpringBootTest
class RecruiterJobOfferControllerTest {

    private static final String URL = "/api/v1/recruiters/jobs";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${joyn.security.jwt.secret}")
    private String jwtSecret;

    private MockMvc mockMvc;

    /** Suffixe unique pour ne pas entrer en collision avec des données existantes. */
    private final String run = UUID.randomUUID().toString().substring(0, 8);

    private Long recruiterUserId;
    private Long candidateUserId;
    private Long orphanRecruiterUserId;
    private Long companyId;
    private Long recruiterProfileId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        companyId = insertReturningId("INSERT INTO companies (name) VALUES (?) RETURNING id", "Entreprise Beta " + run);
        recruiterUserId = insertUser("camille-" + run + "@beta.fr", "RECRUITER");
        candidateUserId = insertUser("ariel-" + run + "@mail.fr", "CANDIDATE");
        orphanRecruiterUserId = insertUser("sans-profil-" + run + "@beta.fr", "RECRUITER");
        recruiterProfileId = insertReturningId(
                "INSERT INTO recruiter_profiles (user_id, company_id, first_name, last_name) VALUES (?, ?, ?, ?) RETURNING id",
                recruiterUserId, companyId, "Camille", "Martin");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM job_offers WHERE company_id = ?", companyId);
        jdbc.update("DELETE FROM recruiter_profiles WHERE company_id = ?", companyId);
        jdbc.update("DELETE FROM companies WHERE id = ?", companyId);
        jdbc.update("DELETE FROM users WHERE id IN (?, ?, ?)", recruiterUserId, candidateUserId, orphanRecruiterUserId);
        jdbc.update("DELETE FROM skills WHERE name LIKE ?", "%" + run + "%");
    }

    // ---------------------------------------------------------------- 201

    @Test
    void createsOfferForRecruiterCompany() throws Exception {
        String react = "React-" + run;
        String docker = "Docker-" + run;

        MvcResult result = mockMvc.perform(post(URL)
                        .header("Authorization", bearer(recruiterUserId, "RECRUITER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(react, docker)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith(URL + "/")))
                .andExpect(jsonPath("$.company_id").value(companyId))
                .andExpect(jsonPath("$.recruiter_id").value(recruiterProfileId))
                .andExpect(jsonPath("$.title").value("Développeur·se Full-Stack en alternance"))
                .andExpect(jsonPath("$.contract_type").value("APPRENTICESHIP"))
                .andExpect(jsonPath("$.remote_policy").value("HYBRID"))
                .andExpect(jsonPath("$.experience_level").value("MID"))
                .andExpect(jsonPath("$.salary_period").value("MONTH"))
                .andExpect(jsonPath("$.rhythm_type").value("3 sem / 1 sem"))
                .andExpect(jsonPath("$.start_date").value("2027-09-01"))
                .andExpect(jsonPath("$.duration_months").value(24))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.skills", containsInAnyOrder(react, docker)))
                .andExpect(jsonPath("$.created_at").exists())
                .andReturn();

        Long offerId = jdbc.queryForObject("SELECT id FROM job_offers WHERE company_id = ?", Long.class, companyId);
        assertThat(result.getResponse().getHeader("Location")).isEqualTo(URL + "/" + offerId);
        Integer linkedSkills = jdbc.queryForObject(
                "SELECT count(*) FROM job_offer_skills WHERE job_offer_id = ?", Integer.class, offerId);
        assertThat(linkedSkills).isEqualTo(2);
        Integer published = jdbc.queryForObject(
                "SELECT count(*) FROM job_offers WHERE id = ? AND published_at IS NOT NULL", Integer.class, offerId);
        assertThat(published).isEqualTo(1);
    }

    @Test
    void reusesExistingSkillInsteadOfCreatingDuplicate() throws Exception {
        String existing = "Java-" + run;
        jdbc.update("INSERT INTO skills (name) VALUES (?)", existing);

        mockMvc.perform(post(URL)
                        .header("Authorization", bearer(recruiterUserId, "RECRUITER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(existing.toLowerCase(), "  " + existing + "  ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.skills.length()").value(1));

        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM skills WHERE lower(name) = lower(?)", Integer.class, existing);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void savesDraftWhenRequested() throws Exception {
        String body = validBody("Go-" + run, "SQL-" + run).replace("\"title\"", "\"status\": \"DRAFT\", \"title\"");

        mockMvc.perform(post(URL)
                        .header("Authorization", bearer(recruiterUserId, "RECRUITER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.published_at").value(nullValue()));
    }

    // ---------------------------------------------------------------- 401 / 403

    @Test
    void returns401WithoutToken() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("A-" + run, "B-" + run)))
                .andExpect(status().isUnauthorized());
        assertNoOfferCreated();
    }

    @Test
    void returns401WithTokenSignedWithAnotherKey() throws Exception {
        String forged = tokenSignedWith("une-autre-cle-secrete-de-plus-de-32-caracteres!", recruiterUserId, "RECRUITER");
        mockMvc.perform(post(URL)
                        .header("Authorization", "Bearer " + forged)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("A-" + run, "B-" + run)))
                .andExpect(status().isUnauthorized());
        assertNoOfferCreated();
    }

    @Test
    void returns403ForCandidate() throws Exception {
        mockMvc.perform(post(URL)
                        .header("Authorization", bearer(candidateUserId, "CANDIDATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("A-" + run, "B-" + run)))
                .andExpect(status().isForbidden());
        assertNoOfferCreated();
    }

    @Test
    void returns403ForRecruiterWithoutProfile() throws Exception {
        mockMvc.perform(post(URL)
                        .header("Authorization", bearer(orphanRecruiterUserId, "RECRUITER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("A-" + run, "B-" + run)))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- 400

    @Test
    void returns400WithEveryMissingField() throws Exception {
        mockMvc.perform(post(URL)
                        .header("Authorization", bearer(recruiterUserId, "RECRUITER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skills\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("title")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("contract_type")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("location")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("remote_policy")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("description")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("skills")));
        assertNoOfferCreated();
    }

    @Test
    void returns400WhenSalaryMaxIsLowerThanSalaryMin() throws Exception {
        String body = validBody("A-" + run, "B-" + run)
                .replace("\"salary_min\": 1450", "\"salary_min\": 2000");

        mockMvc.perform(post(URL)
                        .header("Authorization", bearer(recruiterUserId, "RECRUITER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("salary_max"));
        assertNoOfferCreated();
    }

    @Test
    void returns400ForUnknownContractType() throws Exception {
        String body = validBody("A-" + run, "B-" + run).replace("APPRENTICESHIP", "CONTRAT_INCONNU");

        mockMvc.perform(post(URL)
                        .header("Authorization", bearer(recruiterUserId, "RECRUITER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("contract_type"));
        assertNoOfferCreated();
    }

    @Test
    void returns400ForInvalidRemotePolicy() throws Exception {
        String body = validBody("A-" + run, "B-" + run).replace("\"HYBRID\"", "\"PARFOIS\"");

        mockMvc.perform(post(URL)
                        .header("Authorization", bearer(recruiterUserId, "RECRUITER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("remote_policy"));
    }

    @Test
    void returns400ForMalformedJson() throws Exception {
        mockMvc.perform(post(URL)
                        .header("Authorization", bearer(recruiterUserId, "RECRUITER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ ceci n'est pas du JSON"))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- outils

    private static String validBody(String skill1, String skill2) {
        return """
                {
                  "title": "Développeur·se Full-Stack en alternance",
                  "contract_type": "APPRENTICESHIP",
                  "location": "Paris 9e",
                  "remote_policy": "HYBRID",
                  "experience_level": "MID_LEVEL",
                  "description": "Rejoins l'équipe Paiements.",
                  "salary_min": 1450,
                  "salary_max": 1600,
                  "currency": "EUR",
                  "salary_period": "MONTH",
                  "skills": ["%s", "%s"],
                  "rhythm_type": "3 sem / 1 sem",
                  "start_date": "2027-09-01",
                  "duration_months": 24,
                  "degree_level_required": "BAC_5"
                }""".formatted(skill1, skill2);
    }

    private void assertNoOfferCreated() {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM job_offers WHERE company_id = ?", Integer.class, companyId);
        assertThat(count).isZero();
    }

    private Long insertUser(String email, String role) {
        return insertReturningId(
                "INSERT INTO users (email, password_hash, role) VALUES (?, 'hash', ?) RETURNING id", email, role);
    }

    private Long insertReturningId(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private String bearer(Long userId, String role) {
        return "Bearer " + tokenSignedWith(jwtSecret, userId, role);
    }

    /** Fabrique un token comme le fera la route de connexion : HS256, sub = id utilisateur, role. */
    private static String tokenSignedWith(String secret, Long userId, String role) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(SecurityConfig.secretKey(secret)));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
