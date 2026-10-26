package com.gpstl.joyn.application.service;

import com.gpstl.joyn.config.SecurityConfig;
import com.gpstl.joyn.joboffer.JobDatabaseTestSupport;
import com.gpstl.joyn.joboffer.service.JobClosureService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mise à jour du statut d'une candidature dont l'offre a été close (supprimée logiquement),
 * avec le vrai SecurityConfig et de vrais JWT, sur un schéma PostgreSQL temporaire.
 */
class ApplicationStatusOnClosedJobIntegrationTest extends JobDatabaseTestSupport {
    private static final String SCHEMA = "job_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) { configureDatabase(registry, SCHEMA); }

    @Autowired JobClosureService closures;
    @Value("${joyn.security.jwt.secret}") String secret;
    private MockMvc mvc;
    private long application;

    @BeforeEach
    void applicationOnAClosedJob() {
        mvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
        long profile = insert("INSERT INTO candidate_profiles(user_id, first_name, last_name) VALUES (?, 'C', 'D') RETURNING id", candidate);
        int pending = jdbc.queryForObject("SELECT id FROM application_statuses WHERE code = 'PENDING'", Integer.class);
        application = insert("INSERT INTO applications(candidate_id, job_offer_id, status_id) VALUES (?, ?, ?) RETURNING id",
                profile, job, pending);
        // Clôture réelle de l'offre : status = CLOSED et deleted_at renseigné.
        closures.close(recruiter, job, "Filled");
    }

    private String token(long userId, String role) {
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(SecurityConfig.secretKey(secret)));
        var claims = JwtClaimsSet.builder().subject(Long.toString(userId)).claim("role", role)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private String route() {
        return "/api/v1/applications/" + application + "/status";
    }

    private String statusInDatabase() {
        return jdbc.queryForObject("""
            SELECT s.code FROM applications a JOIN application_statuses s ON s.id = a.status_id
            WHERE a.id = ?""", String.class, application);
    }

    @Test
    void ownerCompanyCanStillProcessApplicationsOfAClosedJob() throws Exception {
        // Avant la correction : 404 « Job not found », car la vérification excluait les offres closes.
        mvc.perform(patch(route()).header("Authorization", token(recruiter, "RECRUITER"))
                        .contentType("application/json").content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        assertThat(statusInDatabase()).isEqualTo("REJECTED");
    }

    @Test
    void administratorCanProcessApplicationsOfAClosedJob() throws Exception {
        mvc.perform(patch(route()).header("Authorization", token(admin, "ADMIN"))
                        .contentType("application/json").content("{\"status\":\"IN_REVIEW\"}"))
                .andExpect(status().isOk());
        assertThat(statusInDatabase()).isEqualTo("IN_REVIEW");
    }

    @Test
    void closingTheJobDoesNotOpenItToOtherCompanies() throws Exception {
        mvc.perform(patch(route()).header("Authorization", token(outsider, "RECRUITER"))
                        .contentType("application/json").content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isForbidden());
        assertThat(statusInDatabase()).isEqualTo("PENDING");
    }

    @Test
    void wrongContentTypeGets415InsteadOf500() throws Exception {
        mvc.perform(patch(route()).header("Authorization", token(recruiter, "RECRUITER"))
                        .contentType("text/plain").content("REJECTED"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }
}
