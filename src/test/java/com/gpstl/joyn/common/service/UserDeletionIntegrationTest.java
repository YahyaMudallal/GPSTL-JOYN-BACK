
package com.gpstl.joyn.common.service;

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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class UserDeletionIntegrationTest {

    private static final String URL = "/api/v1/users/";

    @DynamicPropertySource
    static void storageProvider(DynamicPropertyRegistry registry) {
        registry.add("joyn.storage.provider", () -> "local");
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${joyn.security.jwt.secret}")
    private String jwtSecret;

    private MockMvc mockMvc;

    private final String run =
            UUID.randomUUID().toString().substring(0, 8);

    private Long candidateUserId;
    private Long otherCandidateUserId;
    private Long adminUserId;

    @BeforeEach
    void setUp() {

        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        candidateUserId = insertUser(
                "candidate-" + run + "@example.test",
                "CANDIDATE"
        );

        otherCandidateUserId = insertUser(
                "other-" + run + "@example.test",
                "CANDIDATE"
        );

        adminUserId = insertUser(
                "admin-" + run + "@example.test",
                "ADMIN"
        );

        jdbc.update(
                """
                INSERT INTO candidate_profiles
                    (user_id, first_name, last_name)
                VALUES (?, 'Jean', 'Dupont')
                """,
                candidateUserId
        );
    }

    @AfterEach
    void tearDown() {

        // Remove audit records created by these tests.
        jdbc.update(
                """
                DELETE FROM user_deletion_audit
                WHERE deleted_user_id IN (?, ?, ?)
                """,
                candidateUserId,
                otherCandidateUserId,
                adminUserId
        );

        // Remove temporary candidate profiles.
        jdbc.update(
                """
                DELETE FROM candidate_profiles
                WHERE user_id IN (?, ?)
                """,
                candidateUserId,
                otherCandidateUserId
        );

        // Remove temporary users.
        jdbc.update(
                """
                DELETE FROM users
                WHERE id IN (?, ?, ?)
                """,
                candidateUserId,
                otherCandidateUserId,
                adminUserId
        );
    }

    @Test
    void deletingWithoutJwtReturns401() throws Exception {

        mockMvc.perform(
                delete(URL + candidateUserId)
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void candidateCannotDeleteAnotherAccount() throws Exception {

        mockMvc.perform(
                delete(URL + otherCandidateUserId)
                        .header(
                                "Authorization",
                                bearer(candidateUserId, "CANDIDATE")
                        )
        ).andExpect(status().isForbidden());

        String status = jdbc.queryForObject(
                "SELECT status FROM users WHERE id = ?",
                String.class,
                otherCandidateUserId
        );

        assertEquals("ACTIVE", status);
    }

    @Test
    void administratorCannotDeleteOwnAccount() throws Exception {

        mockMvc.perform(
                delete(URL + adminUserId)
                        .header(
                                "Authorization",
                                bearer(adminUserId, "ADMIN")
                        )
        ).andExpect(status().isForbidden());

        String status = jdbc.queryForObject(
                "SELECT status FROM users WHERE id = ?",
                String.class,
                adminUserId
        );

        assertEquals("ACTIVE", status);
    }

    @Test
    void candidateCanDeleteOwnAccount() throws Exception {

        String candidateToken =
                bearer(candidateUserId, "CANDIDATE");

        mockMvc.perform(
                delete(URL + candidateUserId)
                        .header("Authorization", candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"reason":"Account no longer needed"}
                                """
                        )
        ).andExpect(status().isNoContent());

        // Check soft deletion.
        String status = jdbc.queryForObject(
                "SELECT status FROM users WHERE id = ?",
                String.class,
                candidateUserId
        );

        assertEquals("DELETED", status);

        // Check deletion timestamp.
        Integer timestampCount = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM users
                WHERE id = ?
                  AND deleted_at IS NOT NULL
                """,
                Integer.class,
                candidateUserId
        );

        assertEquals(1, timestampCount);

        // Check email anonymization.
        String email = jdbc.queryForObject(
                "SELECT email FROM users WHERE id = ?",
                String.class,
                candidateUserId
        );

        assertEquals(
                "deleted-" + candidateUserId + "@anonymized.local",
                email
        );

        // Check candidate profile anonymization.
        String firstName = jdbc.queryForObject(
                """
                SELECT first_name
                FROM candidate_profiles
                WHERE user_id = ?
                """,
                String.class,
                candidateUserId
        );

        assertEquals("Compte", firstName);

        String lastName = jdbc.queryForObject(
                """
                SELECT last_name
                FROM candidate_profiles
                WHERE user_id = ?
                """,
                String.class,
                candidateUserId
        );

        assertEquals("Supprimé", lastName);

        // Check audit logging.
        Integer auditCount = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM user_deletion_audit
                WHERE deleted_user_id = ?
                  AND deleted_by = ?
                  AND reason = ?
                """,
                Integer.class,
                candidateUserId,
                candidateUserId,
                "Account no longer needed"
        );

        assertEquals(1, auditCount);

        // Old JWT must be rejected immediately.
        mockMvc.perform(
                delete(URL + otherCandidateUserId)
                        .header("Authorization", candidateToken)
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void administratorCanDeleteAnotherUser() throws Exception {

        mockMvc.perform(
                delete(URL + candidateUserId)
                        .header(
                                "Authorization",
                                bearer(adminUserId, "ADMIN")
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"reason":"Administrative deletion"}
                                """
                        )
        ).andExpect(status().isNoContent());

        String status = jdbc.queryForObject(
                "SELECT status FROM users WHERE id = ?",
                String.class,
                candidateUserId
        );

        assertEquals("DELETED", status);

        Integer auditCount = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM user_deletion_audit
                WHERE deleted_user_id = ?
                  AND deleted_by = ?
                """,
                Integer.class,
                candidateUserId,
                adminUserId
        );

        assertEquals(1, auditCount);

        // Repeated deletion returns 404.
        mockMvc.perform(
                delete(URL + candidateUserId)
                        .header(
                                "Authorization",
                                bearer(adminUserId, "ADMIN")
                        )
        ).andExpect(status().isNotFound());
    }

    private Long insertUser(String email, String role) {

        return jdbc.queryForObject(
                """
                INSERT INTO users
                    (email, password_hash, role, status)
                VALUES (?, 'test-hash', ?, 'ACTIVE')
                RETURNING id
                """,
                Long.class,
                email,
                role
        );
    }

    private String bearer(Long userId, String role) {

        NimbusJwtEncoder encoder = new NimbusJwtEncoder(
                new ImmutableSecret<>(
                        SecurityConfig.secretKey(jwtSecret)
                )
        );

        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .build();

        return "Bearer " + encoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
    }
}
