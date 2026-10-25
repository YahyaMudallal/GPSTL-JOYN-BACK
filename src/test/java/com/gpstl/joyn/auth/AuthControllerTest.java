package com.gpstl.joyn.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'intégration de POST /api/v1/auth/login (U16), sur la vraie base PostgreSQL.
 */
@SpringBootTest
class AuthControllerTest {

    private static final String URL = "/api/v1/auth/login";
    private static final String PASSWORD = "MotDePasse123!";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    private MockMvc mockMvc;

    /** Suffixe unique : chaque test a ses propres emails (le compteur brute-force est par email). */
    private final String run = UUID.randomUUID().toString().substring(0, 8);

    private String recruiterEmail;
    private Long recruiterId;
    private String candidateEmail;
    private Long candidateId;
    private String suspendedEmail;
    private Long suspendedId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        recruiterEmail = "recruteur-" + run + "@joyn.test";
        candidateEmail = "candidat-" + run + "@joyn.test";
        suspendedEmail = "suspendu-" + run + "@joyn.test";
        recruiterId = insertUser(recruiterEmail, "RECRUITER", "ACTIVE");
        candidateId = insertUser(candidateEmail, "CANDIDATE", "ACTIVE");
        suspendedId = insertUser(suspendedEmail, "RECRUITER", "SUSPENDED");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM users WHERE id IN (?, ?, ?)", recruiterId, candidateId, suspendedId);
    }

    // ---------------------------------------------------------------- 200

    @Test
    void returnsTokenContainingUserIdAndRole() throws Exception {
        String body = login(recruiterEmail, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(3600))
                .andExpect(jsonPath("$.role").value("RECRUITER"))
                .andReturn().getResponse().getContentAsString();

        // Le token est décodé avec le même décodeur que SecurityConfig : il est donc accepté par l'API.
        Jwt jwt = jwtDecoder.decode(JsonPath.read(body, "$.access_token"));
        assertThat(jwt.getSubject()).isEqualTo(String.valueOf(recruiterId));
        assertThat(jwt.getClaimAsString("role")).isEqualTo("RECRUITER");
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now());
    }

        @Test
    void emailIsCaseInsensitive() throws Exception {
        login(candidateEmail.toUpperCase(), PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CANDIDATE"));
    }

    @Test
    void recordsLastLoginDate() throws Exception {
        login(candidateEmail, PASSWORD).andExpect(status().isOk());

        Timestamp lastLogin = jdbc.queryForObject("SELECT last_login_at FROM users WHERE id = ?", Timestamp.class, candidateId);
        assertThat(lastLogin).isNotNull();
    }

    @Test
    void tokenFromLoginWorksOnProtectedRoutes() throws Exception {
        // Token candidat sur une route recruteur : le token est reconnu (pas 401) mais le rôle est refusé (403).
        String candidateToken = tokenOf(candidateEmail);
        mockMvc.perform(post("/api/v1/recruiters/jobs")
                        .header("Authorization", "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        // Token recruteur : la sécurité laisse passer, c'est la validation du corps vide qui répond 400.
        String recruiterToken = tokenOf(recruiterEmail);
        mockMvc.perform(post("/api/v1/recruiters/jobs")
                        .header("Authorization", "Bearer " + recruiterToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void defaultAdminCanLogIn() throws Exception {
        // Compte créé par la migration V3__create_default_admin.sql
        login("admin@joyn.test", "JoynAdmin2026!")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    // ---------------------------------------------------------------- 401

    @Test
    void rejectsWrongPassword() throws Exception {
        login(recruiterEmail, "mauvais-mot-de-passe")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email ou mot de passe incorrect"));
    }

    @Test
    void unknownEmailGivesExactlyTheSameError() throws Exception {
        login("personne-" + run + "@joyn.test", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email ou mot de passe incorrect"));
    }

    // ---------------------------------------------------------------- 403

    @Test
    void rejectsSuspendedAccount() throws Exception {
        login(suspendedEmail, PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Ce compte est suspendu"));
    }

    // ---------------------------------------------------------------- 400

    @Test
    void rejectsMissingFields() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("email", "password")));
    }

    @Test
    void rejectsMalformedEmail() throws Exception {
        login("pas-un-email", PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    @Test
    void rejectsInvalidJson() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{pas du json"))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- 429

    @Test
    void blocksAfterFiveFailedAttempts() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(candidateEmail, "faux-" + i).andExpect(status().isUnauthorized());
        }
        // Même avec le BON mot de passe, l'email est maintenant bloqué.
        login(candidateEmail, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    // ---------------------------------------------------------------- outils

    private ResultActions login(String email, String password) throws Exception {
        String json = """
                {"email": "%s", "password": "%s"}""".formatted(email, password);
        return mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String tokenOf(String email) throws Exception {
        String body = login(email, PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.access_token");
    }

    private Long insertUser(String email, String role, String status) {
        return jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, role, status) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, email, passwordEncoder.encode(PASSWORD), role, status);
    }
}
