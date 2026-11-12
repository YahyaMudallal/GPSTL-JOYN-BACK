package com.gpstl.joyn.candidate.controller;

import com.gpstl.joyn.config.SecurityConfig;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'intégration de GET /api/v1/candidates/dashboard (CA36), sur la vraie base PostgreSQL.
 *
 * Données créées avant chaque test pour la candidate "Lina" :
 *  - 3 candidatures : Dev Java il y a 40 jours (REJECTED), Data il y a 2 jours (PENDING, vue hier),
 *    Back il y a 10 jours (INTERVIEW) ;
 *  - 3 swipes : LIKE sur Data (il y a 3 jours), PASS sur Back (il y a 12 jours),
 *    LIKE sur Mobile (il y a 4 jours, sans candidature : compte comme "Intéressé") ;
 *  - 1 match sur Data (hier, 92 %), avec une conversation : 1 message du recruteur non lu, 1 message de Lina ;
 *  - 1 entretien confirmé dans 2 jours, planifié il y a 6 heures.
 * Tout est supprimé après chaque test (emails et entreprise contiennent le suffixe unique "run").
 */
@SpringBootTest
class CandidateDashboardControllerTest {

    private static final String URL = "/api/v1/candidates/dashboard";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${joyn.security.jwt.secret}")
    private String jwtSecret;

    private MockMvc mockMvc;

    private final String run = UUID.randomUUID().toString().substring(0, 8);
    private long companyId;
    private long candidateUserId;
    private long otherCandidateUserId;
    private long recruiterUserId;
    private long adminUserId;
    private long conversationId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        Instant now = Instant.now();

        companyId = id("INSERT INTO companies (name) VALUES (?) RETURNING id", "Acme " + run);

        recruiterUserId = insertUser("rec", "RECRUITER");
        adminUserId = insertUser("admin", "ADMIN");
        long recruiterId = id("""
                INSERT INTO recruiter_profiles (user_id, company_id, first_name, last_name)
                VALUES (?, ?, 'Rita', 'Recruteuse') RETURNING id""", recruiterUserId, companyId);

        candidateUserId = insertUser("lina", "CANDIDATE");
        long candidateId = insertCandidateProfile(candidateUserId, "Lina");

        // Une autre candidate, sans aucune activité : elle ne doit rien voir des chiffres de Lina.
        otherCandidateUserId = insertUser("omar", "CANDIDATE");
        insertCandidateProfile(otherCandidateUserId, "Omar");

        long devJava = insertOffer(recruiterId, "Dev Java " + run);
        long data = insertOffer(recruiterId, "Data " + run);
        long back = insertOffer(recruiterId, "Back " + run);
        long mobile = insertOffer(recruiterId, "Mobile " + run);

        insertApplication(candidateId, devJava, "REJECTED", now.minus(Duration.ofDays(40)), null);
        insertApplication(candidateId, data, "PENDING", now.minus(Duration.ofDays(2)), now.minus(Duration.ofDays(1)));
        insertApplication(candidateId, back, "INTERVIEW", now.minus(Duration.ofDays(10)), null);

        jdbc.update("INSERT INTO job_swipes (candidate_id, job_offer_id, action, created_at) VALUES (?, ?, 'LIKE', ?)",
                candidateId, data, ts(now.minus(Duration.ofDays(3))));
        jdbc.update("INSERT INTO job_swipes (candidate_id, job_offer_id, action, created_at) VALUES (?, ?, 'PASS', ?)",
                candidateId, back, ts(now.minus(Duration.ofDays(12))));
        jdbc.update("INSERT INTO job_swipes (candidate_id, job_offer_id, action, created_at) VALUES (?, ?, 'LIKE', ?)",
                candidateId, mobile, ts(now.minus(Duration.ofDays(4))));

        long matchId = id("""
                INSERT INTO matches (candidate_id, job_offer_id, compatibility_score, status, created_at)
                VALUES (?, ?, 92, 'ACTIVE', ?) RETURNING id""", candidateId, data, ts(now.minus(Duration.ofDays(1))));
        conversationId = id("INSERT INTO conversations (match_id) VALUES (?) RETURNING id", matchId);
        jdbc.update("INSERT INTO messages (conversation_id, sender_user_id, content, created_at) VALUES (?, ?, 'Bonjour', ?)",
                conversationId, recruiterUserId, ts(now.minus(Duration.ofHours(12))));
        jdbc.update("INSERT INTO messages (conversation_id, sender_user_id, content, created_at) VALUES (?, ?, 'Merci', ?)",
                conversationId, candidateUserId, ts(now.minus(Duration.ofHours(11))));

        jdbc.update("""
                INSERT INTO interviews (match_id, scheduled_at, status, created_at)
                VALUES (?, ?, 'CONFIRMED', ?)""",
                matchId, ts(now.plus(Duration.ofDays(2))), ts(now.minus(Duration.ofHours(6))));
    }

    @AfterEach
    void tearDown() {
        // Ordre imposé par les clés étrangères (ON DELETE RESTRICT sur applications et job_offers).
        jdbc.update("DELETE FROM applications WHERE job_offer_id IN (SELECT id FROM job_offers WHERE company_id = ?)", companyId);
        jdbc.update("DELETE FROM job_offers WHERE company_id = ?", companyId); // supprime aussi swipes, matches, messages...
        jdbc.update("DELETE FROM users WHERE email LIKE ?", "%-" + run + "@dashboard.test");
        jdbc.update("DELETE FROM companies WHERE id = ?", companyId);
    }

    // ---------------------------------------------------------------- sécurité

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsRecruiterAndAdmin() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", bearer(recruiterUserId, "RECRUITER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(URL).header("Authorization", bearer(adminUserId, "ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsSuspendedCandidate() throws Exception {
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", candidateUserId);
        mockMvc.perform(get(URL).header("Authorization", bearer(candidateUserId, "CANDIDATE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsNotFoundWhenCandidateHasNoProfile() throws Exception {
        long withoutProfile = insertUser("noprofile", "CANDIDATE");
        mockMvc.perform(get(URL).header("Authorization", bearer(withoutProfile, "CANDIDATE")))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- 200

    @Test
    void returnsAllCountersForPeriodAllByDefault() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", bearer(candidateUserId, "CANDIDATE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("all"))
                // Carte "Candidatures en cours" : Intéressé 1 (Mobile), Envoyée 1, Vue 0, Entretien 1
                .andExpect(jsonPath("$.pipeline.total").value(3))
                .andExpect(jsonPath("$.pipeline.interested").value(1))
                .andExpect(jsonPath("$.pipeline.sent").value(1))
                .andExpect(jsonPath("$.pipeline.viewed").value(0))
                .andExpect(jsonPath("$.pipeline.interview").value(1))
                .andExpect(jsonPath("$.applications.total").value(3))
                .andExpect(jsonPath("$.applications.active").value(2))
                .andExpect(jsonPath("$.applications.by_status.PENDING").value(1))
                .andExpect(jsonPath("$.applications.by_status.INTERVIEW").value(1))
                .andExpect(jsonPath("$.applications.by_status.REJECTED").value(1))
                .andExpect(jsonPath("$.applications.by_status.ACCEPTED").value(0))
                .andExpect(jsonPath("$.swipes.total").value(3))
                .andExpect(jsonPath("$.swipes.likes").value(2))
                .andExpect(jsonPath("$.swipes.passes").value(1))
                .andExpect(jsonPath("$.matches.total").value(1))
                .andExpect(jsonPath("$.matches.active").value(1))
                // Carte "Nouveaux Matchs"
                .andExpect(jsonPath("$.recent_matches", hasSize(1)))
                .andExpect(jsonPath("$.recent_matches[0].job_title").value("Data " + run))
                .andExpect(jsonPath("$.recent_matches[0].company_name").value("Acme " + run))
                .andExpect(jsonPath("$.recent_matches[0].compatibility_score").value(92))
                .andExpect(jsonPath("$.recent_matches[0].conversation_id").value(conversationId))
                .andExpect(jsonPath("$.conversations.total").value(1))
                .andExpect(jsonPath("$.conversations.unread_messages").value(1))
                .andExpect(jsonPath("$.interviews.upcoming").value(1))
                .andExpect(jsonPath("$.interviews.next.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.interviews.next.job_title").value("Data " + run))
                .andExpect(jsonPath("$.interviews.next.company_name").value("Acme " + run))
                // Bouton "Ouvrir la discussion"
                .andExpect(jsonPath("$.interviews.next.conversation_id").value(conversationId))
                // 3 envois + 1 "vue" + 2 likes + 1 match + 1 message reçu + 1 entretien = 9 événements
                .andExpect(jsonPath("$.recent_activities", hasSize(9)))
                .andExpect(jsonPath("$.recent_activities[0].type").value("INTERVIEW_PLANNED"))
                .andExpect(jsonPath("$.recent_activities[1].type").value("MESSAGE_RECEIVED"));
    }

    @Test
    void statsRouteReturnsTheSameDashboard() throws Exception {
        mockMvc.perform(get(URL + "/stats").header("Authorization", bearer(candidateUserId, "CANDIDATE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applications.total").value(3));
    }

    @Test
    void filtersCountersByPeriod() throws Exception {
        // 7 derniers jours : seulement la candidature Data (il y a 2 jours) et le like (il y a 3 jours).
        mockMvc.perform(get(URL).param("period", "7d").header("Authorization", bearer(candidateUserId, "CANDIDATE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("7d"))
                .andExpect(jsonPath("$.applications.total").value(1))
                .andExpect(jsonPath("$.applications.by_status.PENDING").value(1))
                // "active" décrit l'état actuel : il ne dépend pas de la période.
                .andExpect(jsonPath("$.applications.active").value(2))
                .andExpect(jsonPath("$.swipes.likes").value(2))
                .andExpect(jsonPath("$.swipes.passes").value(0))
                // "Intéressé" décrit aussi la situation actuelle.
                .andExpect(jsonPath("$.pipeline.interested").value(1));

        // 30 derniers jours : Data + Back, pas Dev Java (40 jours).
        mockMvc.perform(get(URL).param("period", "30d").header("Authorization", bearer(candidateUserId, "CANDIDATE")))
                .andExpect(jsonPath("$.applications.total").value(2))
                .andExpect(jsonPath("$.applications.by_status.REJECTED").value(0))
                .andExpect(jsonPath("$.swipes.total").value(3));
    }

    @Test
    void candidateOnlySeesOwnData() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", bearer(otherCandidateUserId, "CANDIDATE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applications.total").value(0))
                .andExpect(jsonPath("$.swipes.total").value(0))
                .andExpect(jsonPath("$.matches.total").value(0))
                .andExpect(jsonPath("$.pipeline.total").value(0))
                .andExpect(jsonPath("$.recent_matches", hasSize(0)))
                .andExpect(jsonPath("$.conversations.unread_messages").value(0))
                .andExpect(jsonPath("$.interviews.upcoming").value(0))
                .andExpect(jsonPath("$.interviews.next").value(nullValue()))
                .andExpect(jsonPath("$.recent_activities", hasSize(0)));
    }

    @Test
    void newOffersExcludeOffersAlreadySwipedOrApplied() throws Exception {
        // La base peut contenir d'autres offres récentes : on compare donc Lina à Omar, qui n'a rien fait.
        // Les 4 offres de ce test (publiées aujourd'hui) sont "nouvelles" pour Omar, mais Lina les a
        // toutes déjà swipées ou postulées : Lina doit en voir exactement 4 de moins.
        long forLina = newOffersCount(candidateUserId);
        long forOmar = newOffersCount(otherCandidateUserId);
        assertEquals(forOmar - 4, forLina);
    }

    // ---------------------------------------------------------------- 400

    @Test
    void rejectsUnknownPeriod() throws Exception {
        mockMvc.perform(get(URL).param("period", "1y").header("Authorization", bearer(candidateUserId, "CANDIDATE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("period"));
    }

    // ---------------------------------------------------------------- outils

    private long newOffersCount(long userId) throws Exception {
        String json = mockMvc.perform(get(URL).header("Authorization", bearer(userId, "CANDIDATE")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Number count = JsonPath.read(json, "$.new_offers_count");
        return count.longValue();
    }

    private long insertUser(String name, String role) {
        return id("INSERT INTO users (email, password_hash, role) VALUES (?, 'not-a-real-hash', ?) RETURNING id",
                name + "-" + run + "@dashboard.test", role);
    }

    private long insertCandidateProfile(long userId, String firstName) {
        return id("INSERT INTO candidate_profiles (user_id, first_name, last_name) VALUES (?, ?, 'Test') RETURNING id",
                userId, firstName);
    }

    /** Offre publiée en alternance (contract_types.id = 4 dans V1). */
    private long insertOffer(long recruiterId, String title) {
        return id("""
                INSERT INTO job_offers (company_id, recruiter_id, contract_type_id, title, description, status, published_at)
                VALUES (?, ?, (SELECT id FROM contract_types WHERE code = 'APPRENTICESHIP'), ?, 'Description', 'PUBLISHED', NOW())
                RETURNING id""", companyId, recruiterId, title);
    }

    private void insertApplication(long candidateId, long offerId, String status, Instant createdAt, Instant viewedAt) {
        jdbc.update("""
                INSERT INTO applications (candidate_id, job_offer_id, status_id, created_at, updated_at, viewed_at)
                VALUES (?, ?, (SELECT id FROM application_statuses WHERE code = ?), ?, ?, ?)""",
                candidateId, offerId, status, ts(createdAt), ts(createdAt), viewedAt == null ? null : ts(viewedAt));
    }

    private long id(String sql, Object... args) {
        Long id = jdbc.queryForObject(sql, Long.class, args);
        return id == null ? 0 : id;
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }

    /** Token signé comme ceux de la route de connexion (U16). */
    private String bearer(long userId, String role) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(SecurityConfig.secretKey(jwtSecret)));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
