package com.gpstl.joyn.joboffer;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import com.gpstl.joyn.config.SecurityConfig;
import com.gpstl.joyn.joboffer.exception.JobApiException;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Prépare les données communes ; chaque classe de test utilise son propre schéma temporaire. */
@SpringBootTest
@Transactional
abstract class JobDatabaseTestSupport {
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected WebApplicationContext web;
    @Value("${joyn.security.jwt.secret}") String jwtSecret;
    protected long admin, recruiter, candidate, outsider, company, profile, job, skill;
    protected int contract;

    protected static void configureDatabase(DynamicPropertyRegistry registry, String schema) {
        String url = System.getenv().getOrDefault("SPRING_DATASOURCE_URL",
            System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/joyn_db"));
        registry.add("spring.datasource.url", () -> url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema);
        registry.add("spring.flyway.schemas", () -> schema);
        registry.add("spring.flyway.default-schema", () -> schema);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> schema);
    }

    // Le SQL de test prépare et vérifie les données indépendamment du code JPA testé.
    @BeforeEach void seed() {
        admin = user("ADMIN"); recruiter = user("RECRUITER"); candidate = user("CANDIDATE"); outsider = user("RECRUITER");
        company = insert("INSERT INTO companies(name) VALUES ('Owner') RETURNING id");
        long otherCompany = insert("INSERT INTO companies(name) VALUES ('Other') RETURNING id");
        profile = insert("INSERT INTO recruiter_profiles(user_id, company_id, first_name, last_name) VALUES (?, ?, 'A', 'B') RETURNING id", recruiter, company);
        insert("INSERT INTO recruiter_profiles(user_id, company_id, first_name, last_name) VALUES (?, ?, 'C', 'D') RETURNING id", outsider, otherCompany);
        contract = jdbc.queryForObject("SELECT id FROM contract_types WHERE code = 'CDI'", Integer.class);
        job = insert("""
            INSERT INTO job_offers(company_id, recruiter_id, contract_type_id, title, description,
                salary_min, salary_max, status, updated_at)
            VALUES (?, ?, ?, 'Original', 'Description', 50000, 60000, 'PUBLISHED', NOW() - INTERVAL '1 day') RETURNING id
            """, company, profile, contract);
        skill = insert("INSERT INTO skills(name) VALUES (?) RETURNING id", "Skill-" + UUID.randomUUID());
        jdbc.update("INSERT INTO job_offer_skills(job_offer_id, skill_id, required_level) VALUES (?, ?, 'ADVANCED')", job, skill);
    }

    // Le contrôle du nom empêche de supprimer par erreur un schéma applicatif.
    @AfterAll static void cleanup(@Autowired JdbcTemplate jdbc) {
        String schema = jdbc.queryForObject("SELECT current_schema()", String.class);
        if (schema == null || !schema.matches("job_test_[0-9a-f]+")) throw new IllegalStateException("Unexpected test schema");
        jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
    }

    /** Fabrique un JWT signé pour les tests ; ne remplace pas un endpoint de connexion. */
    protected String bearer(long userId) {
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(SecurityConfig.secretKey(jwtSecret)));
        var claims = JwtClaimsSet.builder().subject(Long.toString(userId)).claim("role", "RECRUITER")
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private long user(String role) {
        return insert("INSERT INTO users(email, password_hash, role) VALUES (?, 'test-only', ?) RETURNING id", UUID.randomUUID() + "@example.test", role);
    }

    protected long insert(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }

    protected void assertError(int code, Runnable action) {
        var error = catchThrowableOfType(action::run, JobApiException.class);
        assertThat(error.getStatus()).isEqualTo(code);
    }
}
