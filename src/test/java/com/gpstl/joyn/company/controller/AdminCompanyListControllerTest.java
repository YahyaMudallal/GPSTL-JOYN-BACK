package com.gpstl.joyn.company.controller;

import com.gpstl.joyn.config.SecurityConfig;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'intégration de GET /api/v1/admin/companies (A66), sur la vraie base PostgreSQL et avec de vrais tokens.
 *
 * Trois entreprises de test sont créées ; leur nom contient un suffixe unique ("run"), et chaque test
 * cherche avec search=run pour ne voir que ses propres données, même si la base contient d'autres entreprises.
 */
@SpringBootTest
class AdminCompanyListControllerTest {

    private static final String URL = "/api/v1/admin/companies";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${joyn.security.jwt.secret}")
    private String jwtSecret;

    private MockMvc mockMvc;

    private final String run = UUID.randomUUID().toString().substring(0, 8);
    private String alphaSiret;
    private long adminUserId;
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        adminUserId = jdbc.queryForObject(
        """
        INSERT INTO users (email, password_hash, role, status)
        VALUES (?, 'test-hash', 'ADMIN', 'ACTIVE')
        RETURNING id
        """,
        Long.class,
        "admin-" + run + "@example.test"
        );

        Instant now = Instant.now();
        alphaSiret = randomSiret();
        // Créées de la plus ancienne (Alpha) à la plus récente (Gamma).
        insertCompany("Alpha " + run, alphaSiret, "alpha-" + run + ".fr", "Fintech", "SMALL", "VERIFIED",
                now.minus(Duration.ofDays(3)));
        insertCompany("Beta " + run, randomSiret(), "beta-" + run + ".fr", "Santé", "LARGE", "NOT_VERIFIED",
                now.minus(Duration.ofDays(2)));
        insertCompany("Gamma " + run, randomSiret(), "gamma-" + run + ".fr", "Fintech", "MICRO", "REJECTED",
                now.minus(Duration.ofDays(1)));
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM users WHERE id = ?", adminUserId);
    }

    // ---------------------------------------------------------------- sécurité

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsRecruiterAndCandidate() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", bearer("RECRUITER"))).andExpect(status().isForbidden());
        mockMvc.perform(get(URL).header("Authorization", bearer("CANDIDATE"))).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- 200

    @Test
    void listsCompaniesNewestFirstWithPagination() throws Exception {
        mockMvc.perform(asAdmin(get(URL).param("search", run)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[*].name", contains("Gamma " + run, "Beta " + run, "Alpha " + run)))
                .andExpect(jsonPath("$.data[0].verification_status").value("REJECTED"))
                .andExpect(jsonPath("$.data[0].company_size").value("MICRO"))
                .andExpect(jsonPath("$.pagination.current_page").value(1))
                .andExpect(jsonPath("$.pagination.limit").value(20))
                .andExpect(jsonPath("$.pagination.total_items").value(3))
                .andExpect(jsonPath("$.pagination.total_pages").value(1));
    }

    @Test
    void paginatesResults() throws Exception {
        mockMvc.perform(asAdmin(get(URL).param("search", run).param("limit", "2").param("page", "2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("Alpha " + run))
                .andExpect(jsonPath("$.pagination.current_page").value(2))
                .andExpect(jsonPath("$.pagination.total_items").value(3))
                .andExpect(jsonPath("$.pagination.total_pages").value(2));
    }

    @Test
    void sortsByNameAscending() throws Exception {
        mockMvc.perform(asAdmin(get(URL).param("search", run).param("sort_by", "name").param("order", "asc")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].name", contains("Alpha " + run, "Beta " + run, "Gamma " + run)));
    }

    @Test
    void filtersByVerificationStatusCaseInsensitive() throws Exception {
        mockMvc.perform(asAdmin(get(URL).param("search", run).param("status", "not_verified")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].name", contains("Beta " + run)));
    }

    @Test
    void filtersBySectorAndSize() throws Exception {
        mockMvc.perform(asAdmin(get(URL).param("search", run).param("sector", "fintech")))
                .andExpect(jsonPath("$.data[*].name", contains("Gamma " + run, "Alpha " + run)));

        mockMvc.perform(asAdmin(get(URL).param("search", run).param("sector", "Fintech").param("company_size", "SMALL")))
                .andExpect(jsonPath("$.data[*].name", contains("Alpha " + run)));
    }

    @Test
    void searchesInSiretAndEmailDomain() throws Exception {
        mockMvc.perform(asAdmin(get(URL).param("search", alphaSiret)))
                .andExpect(jsonPath("$.data[*].name", contains("Alpha " + run)));

        mockMvc.perform(asAdmin(get(URL).param("search", ("BETA-" + run).toUpperCase())))
                .andExpect(jsonPath("$.data[*].name", contains("Beta " + run)));
    }

    @Test
    void returnsEmptyPageWhenNothingMatches() throws Exception {
        mockMvc.perform(asAdmin(get(URL).param("search", "aucune-entreprise-" + run)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.pagination.total_items").value(0))
                .andExpect(jsonPath("$.pagination.total_pages").value(0));
    }

    @Test
    void searchTreatsPercentAsNormalCharacter() throws Exception {
        // "%" ne doit pas devenir un joker SQL qui renverrait toutes les entreprises.
        mockMvc.perform(asAdmin(get(URL).param("search", "%")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.total_items").value(0));
    }

    // ---------------------------------------------------------------- 400

    @Test
    void rejectsInvalidPagination() throws Exception {
        expectBadRequest("page", "0", "page");
        expectBadRequest("page", "abc", "page");
        expectBadRequest("limit", "0", "limit");
        expectBadRequest("limit", "101", "limit");
    }

    @Test
    void rejectsUnknownFilterAndSortValues() throws Exception {
        expectBadRequest("status", "BANNED", "status");
        expectBadRequest("company_size", "HUGE", "company_size");
        expectBadRequest("sort_by", "email", "sort_by");
        expectBadRequest("order", "up", "order");
    }

    // ---------------------------------------------------------------- outils

    private void expectBadRequest(String parameter, String value, String expectedField) throws Exception {
        mockMvc.perform(asAdmin(get(URL).param(parameter, value)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value(expectedField));
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", bearer("ADMIN"));
    }

    private void insertCompany(String name, String siret, String emailDomain, String sector, String size,
                               String verificationStatus, Instant createdAt) {
        jdbc.update("""
                        INSERT INTO companies (name, siret, email_domain, sector, company_size, verification_status, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                name, siret, emailDomain, sector, size, verificationStatus,
                Timestamp.from(createdAt), Timestamp.from(createdAt));
    }

    /** SIRET aléatoire de 14 chiffres (la colonne est UNIQUE). */
    private static String randomSiret() {
        return String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000_000_000L, 99_999_999_999_999L));
    }

    /** Token signé comme ceux de la route de connexion (U16). */
    private String bearer(String role) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(SecurityConfig.secretKey(jwtSecret)));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(adminUserId))
                .claim("role", role)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
