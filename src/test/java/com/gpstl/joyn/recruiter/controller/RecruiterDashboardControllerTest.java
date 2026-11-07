package com.gpstl.joyn.recruiter.controller;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'intégration de GET /api/v1/recruiters/dashboard (R49), sur la vraie base PostgreSQL.
 *
 * Deux entreprises : "Acme" (celle de la recruteuse Rita, qu'on teste) et "Rival" (une autre entreprise,
 * dont les chiffres ne doivent JAMAIS apparaître chez Rita).
 *
 * Offres d'Acme : Dev Java (publiée), Data (publiée), Draft (brouillon).
 * Candidatures sur Acme : Lina -> Dev Java il y a 40 jours (refusée, lue après 2 jours),
 *                         Lina -> Data il y a 2 jours (en attente, lue après 1 jour),
 *                         Omar -> Dev Java il y a 5 jours (en attente).
 * Likes sur Acme : Lina aime Data (il y a 3 jours), Omar aime Dev Java (il y a 6 jours).
 * Match Lina / Data (hier, 85 %) + entretien PROPOSÉ dans 2 jours. Rita a évalué le profil de Lina (hier).
 * Lina étudie l'informatique à la Sorbonne, est disponible le 1er septembre 2027 et masque son nom de famille.
 * Chez Rival : 1 candidature et 1 like, à ne pas compter chez Acme.
 * L'offre Data demande 2 compétences ("Java run" et "SQL run") ; Lina a Java, pas SQL.
 */
@SpringBootTest
class RecruiterDashboardControllerTest {

    private static final String URL = "/api/v1/recruiters/dashboard";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${joyn.security.jwt.secret}")
    private String jwtSecret;

    private MockMvc mockMvc;

    private final String run = UUID.randomUUID().toString().substring(0, 8);
    private long acmeId;
    private long rivalId;
    private long ritaUserId;
    private long raviUserId;
    private long linaUserId;
    private long dataOfferId;
    private long draftOfferId;
    private long rivalOfferId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        Instant now = Instant.now();

        acmeId = id("INSERT INTO companies (name) VALUES (?) RETURNING id", "Acme " + run);
        rivalId = id("INSERT INTO companies (name) VALUES (?) RETURNING id", "Rival " + run);

        ritaUserId = insertUser("rita", "RECRUITER");
        long rita = insertRecruiter(ritaUserId, acmeId, "Rita");
        raviUserId = insertUser("ravi", "RECRUITER");
        long ravi = insertRecruiter(raviUserId, rivalId, "Ravi");

        linaUserId = insertUser("lina", "CANDIDATE");
        long lina = insertCandidate(linaUserId, "Lina");
        jdbc.update("UPDATE candidate_profiles SET show_last_name = FALSE, available_from = DATE '2027-09-01' WHERE id = ?", lina);
        jdbc.update("""
                INSERT INTO candidate_educations (candidate_id, school_name, field_of_study, degree, currently_studying)
                VALUES (?, 'Sorbonne Université', 'Informatique', 'MASTERS', TRUE)""", lina);
        long omar = insertCandidate(insertUser("omar", "CANDIDATE"), "Omar");

        long devJavaOfferId = insertOffer(acmeId, rita, "Dev Java " + run, "PUBLISHED", now.minus(Duration.ofDays(20)));
        dataOfferId = insertOffer(acmeId, rita, "Data " + run, "PUBLISHED", now.minus(Duration.ofDays(4)));
        draftOfferId = insertOffer(acmeId, rita, "Draft " + run, "DRAFT", null);
        rivalOfferId = insertOffer(rivalId, ravi, "Rival job " + run, "PUBLISHED", now.minus(Duration.ofDays(4)));

        long java = id("INSERT INTO skills (name) VALUES (?) RETURNING id", "Java " + run);
        long sql = id("INSERT INTO skills (name) VALUES (?) RETURNING id", "SQL " + run);
        jdbc.update("INSERT INTO job_offer_skills (job_offer_id, skill_id) VALUES (?, ?), (?, ?)",
                dataOfferId, java, dataOfferId, sql);
        jdbc.update("INSERT INTO candidate_skills (candidate_id, skill_id) VALUES (?, ?)", lina, java);

        insertApplication(lina, devJavaOfferId, "REJECTED", now.minus(Duration.ofDays(40)), now.minus(Duration.ofDays(38)));
        insertApplication(lina, dataOfferId, "PENDING", now.minus(Duration.ofDays(2)), now.minus(Duration.ofDays(1)));
        insertApplication(omar, devJavaOfferId, "PENDING", now.minus(Duration.ofDays(5)), null);
        insertApplication(lina, rivalOfferId, "PENDING", now.minus(Duration.ofDays(3)), null);

        insertLike(lina, dataOfferId, now.minus(Duration.ofDays(3)));
        insertLike(omar, devJavaOfferId, now.minus(Duration.ofDays(6)));
        insertLike(omar, rivalOfferId, now.minus(Duration.ofDays(1)));

        long matchId = id("""
                INSERT INTO matches (candidate_id, job_offer_id, compatibility_score, status, created_at)
                VALUES (?, ?, 85, 'ACTIVE', ?) RETURNING id""", lina, dataOfferId, ts(now.minus(Duration.ofDays(1))));
        jdbc.update("""
                INSERT INTO interviews (match_id, scheduled_at, status, created_at)
                VALUES (?, ?, 'PROPOSED', ?)""",
                matchId, ts(now.plus(Duration.ofDays(2))), ts(now.minus(Duration.ofHours(6))));
        jdbc.update("""
                INSERT INTO candidate_swipes (recruiter_id, job_offer_id, candidate_id, action, created_at)
                VALUES (?, ?, ?, 'LIKE', ?)""", rita, dataOfferId, lina, ts(now.minus(Duration.ofDays(1))));
    }

    @AfterEach
    void tearDown() {
        // Ordre imposé par les clés étrangères (ON DELETE RESTRICT sur applications, job_offers et companies).
        jdbc.update("""
                DELETE FROM applications
                WHERE job_offer_id IN (SELECT id FROM job_offers WHERE company_id IN (?, ?))""", acmeId, rivalId);
        jdbc.update("DELETE FROM job_offers WHERE company_id IN (?, ?)", acmeId, rivalId);
        jdbc.update("DELETE FROM users WHERE email LIKE ?", "%-" + run + "@dashboard.test");
        jdbc.update("DELETE FROM companies WHERE id IN (?, ?)", acmeId, rivalId);
        // Les liens offre/compétence et candidat/compétence sont déjà partis avec les offres et les profils.
        jdbc.update("DELETE FROM skills WHERE name IN (?, ?)", "Java " + run, "SQL " + run);
    }

    // ---------------------------------------------------------------- sécurité

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsCandidateToken() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", bearer(linaUserId, "CANDIDATE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsSuspendedRecruiter() throws Exception {
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", ritaUserId);
        mockMvc.perform(asRita(get(URL))).andExpect(status().isForbidden());
    }

    @Test
    void rejectsOfferOfAnotherCompany() throws Exception {
        mockMvc.perform(asRita(get(URL).param("job_id", String.valueOf(rivalOfferId))))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsNotFoundForUnknownOffer() throws Exception {
        mockMvc.perform(asRita(get(URL).param("job_id", "999999999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].field").value("job_id"));
    }

    // ---------------------------------------------------------------- 200

    @Test
    void returnsCompanyDashboardForLast30DaysByDefault() throws Exception {
        mockMvc.perform(asRita(get(URL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("30d"))
                .andExpect(jsonPath("$.companyId").value(acmeId))
                .andExpect(jsonPath("$.jobId").value(nullValue()))
                // 30 derniers jours : Lina -> Data et Omar -> Dev Java (pas la candidature de 40 jours, pas Rival)
                .andExpect(jsonPath("$.summary.applications").value(2))
                .andExpect(jsonPath("$.summary.interested").value(2))
                .andExpect(jsonPath("$.summary.matches").value(1))
                .andExpect(jsonPath("$.summary.profilesReviewed").value(1))
                .andExpect(jsonPath("$.summary.activeOffers").value(2))
                .andExpect(jsonPath("$.summary.pendingApplications").value(2))
                .andExpect(jsonPath("$.summary.upcomingInterviews").value(1))
                .andExpect(jsonPath("$.summary.avgResponseDays").value(1.0))
                .andExpect(jsonPath("$.summary.views").value(0))
                // KPI "Candidatures reçues" : 2 contre 1 sur la période précédente -> +100 %
                .andExpect(jsonPath("$.kpis[0].id").value("applications"))
                .andExpect(jsonPath("$.kpis[0].value").value("2"))
                .andExpect(jsonPath("$.kpis[0].deltaPercent").value(100.0))
                .andExpect(jsonPath("$.kpis[2].deltaLabel").value("1 entretien planifié"))
                .andExpect(jsonPath("$.kpis[5].value").value("1,0 j"))
                .andExpect(jsonPath("$.trend", hasSize(30)))
                // Carte "Intérêts reçus" : toujours 8 semaines, S1 à S8
                .andExpect(jsonPath("$.weeklyInterests", hasSize(8)))
                .andExpect(jsonPath("$.weeklyInterests[0].label").value("S1"))
                .andExpect(jsonPath("$.weeklyInterests[7].label").value("S8"))
                // Carte "Candidatures reçues" : la plus récente d'abord ; Lina masque son nom de famille
                .andExpect(jsonPath("$.recentApplications", hasSize(2)))
                .andExpect(jsonPath("$.recentApplications[0].firstName").value("Lina"))
                .andExpect(jsonPath("$.recentApplications[0].lastName").value("T."))
                .andExpect(jsonPath("$.recentApplications[0].education").value("Informatique · Sorbonne Université"))
                .andExpect(jsonPath("$.recentApplications[0].availableFrom").value("2027-09-01"))
                .andExpect(jsonPath("$.recentApplications[0].compatibilityScore").value(85))
                .andExpect(jsonPath("$.recentApplications[0].statusLabel").value("En attente"))
                .andExpect(jsonPath("$.recentApplications[1].firstName").value("Omar"))
                .andExpect(jsonPath("$.recentApplications[1].lastName").value("Test"))
                .andExpect(jsonPath("$.recentApplications[1].compatibilityScore").value(nullValue()))
                // Le brouillon n'apparaît pas ; à égalité, l'offre la plus récente d'abord.
                .andExpect(jsonPath("$.topOffers[*].title", contains("Data " + run, "Dev Java " + run)))
                .andExpect(jsonPath("$.topOffers[0].contract").value("Alternance"))
                .andExpect(jsonPath("$.topOffers[0].status").value("Publiée"))
                .andExpect(jsonPath("$.topOffers[0].tagline").value("1 intéressé · 1 match"))
                .andExpect(jsonPath("$.activities[0].kind").value("interview"))
                .andExpect(jsonPath("$.activities[0].subtitle").value("Lina T. · Data " + run))
                .andExpect(jsonPath("$.priorityActions", hasSize(2)))
                .andExpect(jsonPath("$.priorityActions[0].title").value("2 candidatures en attente"));
    }

    @Test
    void weeklyInterestsCountLikesOfTheLast8Weeks() throws Exception {
        String json = mockMvc.perform(asRita(get(URL).param("period", "7d")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // Les 2 likes sur les offres d'Acme (il y a 3 et 6 jours) sont dans les 8 semaines, même avec period=7d ;
        // le like sur l'offre de Rival n'est pas compté.
        List<Number> values = JsonPath.read(json, "$.weeklyInterests[*].value");
        assertEquals(2, values.stream().mapToLong(Number::longValue).sum());
    }

    @Test
    void statsRouteReturnsTheSameDashboard() throws Exception {
        mockMvc.perform(asRita(get(URL + "/stats")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.applications").value(2));
    }

    @Test
    void periodAllCountsEverythingWithoutComparison() throws Exception {
        mockMvc.perform(asRita(get(URL).param("period", "all")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.applications").value(3))
                .andExpect(jsonPath("$.kpis[0].deltaPercent").value(nullValue()))
                // all : une valeur par semaine sur 12 semaines
                .andExpect(jsonPath("$.trend", hasSize(12)))
                .andExpect(jsonPath("$.profileDistribution[*].label", contains("En attente", "Refusée")));
    }

    @Test
    void jobIdNarrowsEverythingToOneOffer() throws Exception {
        mockMvc.perform(asRita(get(URL).param("period", "7d").param("job_id", String.valueOf(dataOfferId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(dataOfferId))
                .andExpect(jsonPath("$.summary.applications").value(1))
                .andExpect(jsonPath("$.summary.interested").value(1))
                .andExpect(jsonPath("$.summary.activeOffers").value(1))
                .andExpect(jsonPath("$.trend", hasSize(7)))
                .andExpect(jsonPath("$.topOffers", hasSize(1)))
                .andExpect(jsonPath("$.topOffers[0].id").value(dataOfferId));
    }

    @Test
    void jobIdWorksForDraftOffer() throws Exception {
        // Le détail d'une seule offre s'affiche quel que soit son statut.
        mockMvc.perform(asRita(get(URL).param("job_id", String.valueOf(draftOfferId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topOffers[0].statusCode").value("DRAFT"))
                .andExpect(jsonPath("$.summary.applications").value(0));
    }

    @Test
    void otherCompanyOnlySeesItsOwnNumbers() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", bearer(raviUserId, "RECRUITER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value(rivalId))
                .andExpect(jsonPath("$.summary.applications").value(1))
                .andExpect(jsonPath("$.summary.interested").value(1))
                .andExpect(jsonPath("$.summary.matches").value(0))
                .andExpect(jsonPath("$.topOffers[*].title", contains("Rival job " + run)));
    }

    // ---------------------------------------------------------------- /insights

    @Test
    void insightsReturnFunnelChannelsAndHeatmap() throws Exception {
        mockMvc.perform(asRita(get(URL + "/insights")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("30d"))
                .andExpect(jsonPath("$.trend", hasSize(30)))
                // Entonnoir sur 30 jours : 2 likes, 2 candidatures, 1 lue (Lina -> Data), 0 entretien, 0 acceptée
                .andExpect(jsonPath("$.funnel[*].label",
                        contains("Intéressés", "Candidatures", "Lues", "Entretiens", "Acceptées")))
                .andExpect(jsonPath("$.funnel[0].value").value(2))
                .andExpect(jsonPath("$.funnel[1].value").value(2))
                .andExpect(jsonPath("$.funnel[2].value").value(1))
                .andExpect(jsonPath("$.funnel[3].value").value(0))
                // Omar a postulé directement ; Lina a un match sur Data
                .andExpect(jsonPath("$.channels[0].label").value("Candidature directe"))
                .andExpect(jsonPath("$.channels[0].value").value(1))
                .andExpect(jsonPath("$.channels[1].value").value(1))
                // Carte de chaleur : 7 jours x 7 tranches horaires
                .andExpect(jsonPath("$.heatmap.rows", hasSize(7)))
                .andExpect(jsonPath("$.heatmap.columns", hasSize(7)))
                .andExpect(jsonPath("$.heatmap.values", hasSize(7)))
                .andExpect(jsonPath("$.heatmap.values[0]", hasSize(7)))
                // Graphique 3D : pas de données en base, listes vides
                .andExpect(jsonPath("$.surface.xLabels", hasSize(0)));
    }

    @Test
    void insightsHeatmapCountsEveryApplicationOfThePeriod() throws Exception {
        String json = mockMvc.perform(asRita(get(URL + "/insights").param("period", "all")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<List<Number>> values = JsonPath.read(json, "$.heatmap.values");
        long total = values.stream().flatMap(List::stream).mapToLong(Number::longValue).sum();
        assertEquals(3, total); // les 3 candidatures d'Acme, pas celle de Rival
    }

    @Test
    void insightsAreProtectedLikeTheDashboard() throws Exception {
        mockMvc.perform(get(URL + "/insights")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL + "/insights").header("Authorization", bearer(linaUserId, "CANDIDATE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(asRita(get(URL + "/insights").param("job_id", String.valueOf(rivalOfferId))))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- /offers/{jobId}

    @Test
    void offerAnalyticsDescribeOneOffer() throws Exception {
        mockMvc.perform(asRita(get(URL + "/offers/" + dataOfferId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offer.id").value(dataOfferId))
                .andExpect(jsonPath("$.offer.title").value("Data " + run))
                .andExpect(jsonPath("$.offer.status").value("Publiée"))
                .andExpect(jsonPath("$.kpis[0].id").value("applications"))
                .andExpect(jsonPath("$.kpis[0].value").value("1"))
                .andExpect(jsonPath("$.kpis[2].value").value("1"))
                .andExpect(jsonPath("$.trend", hasSize(30)))
                .andExpect(jsonPath("$.sources[1].label").value("Après un match Joyn"))
                .andExpect(jsonPath("$.sources[1].value").value(1))
                .andExpect(jsonPath("$.pipeline[0].label").value("Nouvelles"))
                .andExpect(jsonPath("$.pipeline[0].value").value(1))
                // Lina (seule candidate) a Java mais pas SQL
                .andExpect(jsonPath("$.skills[*].label", contains("Java " + run, "SQL " + run)))
                .andExpect(jsonPath("$.skills[0].value").value(100))
                .andExpect(jsonPath("$.skills[1].value").value(0))
                .andExpect(jsonPath("$.actions[0].title").value("1 candidature en attente"));
    }

    @Test
    void offerAnalyticsWorkForDraftOffer() throws Exception {
        mockMvc.perform(asRita(get(URL + "/offers/" + draftOfferId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offer.statusCode").value("DRAFT"))
                .andExpect(jsonPath("$.kpis[0].value").value("0"))
                .andExpect(jsonPath("$.skills", hasSize(0)));
    }

    @Test
    void offerAnalyticsRejectOtherCompanyUnknownOrInvalidOffer() throws Exception {
        mockMvc.perform(asRita(get(URL + "/offers/" + rivalOfferId))).andExpect(status().isForbidden());
        mockMvc.perform(asRita(get(URL + "/offers/999999999"))).andExpect(status().isNotFound());
        mockMvc.perform(asRita(get(URL + "/offers/abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("jobId"));
        mockMvc.perform(get(URL + "/offers/" + dataOfferId)).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- 400

    @Test
    void rejectsInvalidParameters() throws Exception {
        expectBadRequest("period", "1y");
        expectBadRequest("job_id", "abc");
        expectBadRequest("job_id", "0");
    }

    // ---------------------------------------------------------------- outils

    private void expectBadRequest(String parameter, String value) throws Exception {
        mockMvc.perform(asRita(get(URL).param(parameter, value)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value(parameter));
    }

    private MockHttpServletRequestBuilder asRita(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", bearer(ritaUserId, "RECRUITER"));
    }

    private long insertUser(String name, String role) {
        return id("INSERT INTO users (email, password_hash, role) VALUES (?, 'not-a-real-hash', ?) RETURNING id",
                name + "-" + run + "@dashboard.test", role);
    }

    private long insertRecruiter(long userId, long companyId, String firstName) {
        return id("""
                INSERT INTO recruiter_profiles (user_id, company_id, first_name, last_name)
                VALUES (?, ?, ?, 'Test') RETURNING id""", userId, companyId, firstName);
    }

    private long insertCandidate(long userId, String firstName) {
        return id("INSERT INTO candidate_profiles (user_id, first_name, last_name) VALUES (?, ?, 'Test') RETURNING id",
                userId, firstName);
    }

    private long insertOffer(long companyId, long recruiterId, String title, String status, Instant publishedAt) {
        return id("""
                INSERT INTO job_offers (company_id, recruiter_id, contract_type_id, title, description, status, published_at)
                VALUES (?, ?, (SELECT id FROM contract_types WHERE code = 'APPRENTICESHIP'), ?, 'Description', ?, ?)
                RETURNING id""", companyId, recruiterId, title, status, publishedAt == null ? null : ts(publishedAt));
    }

    private void insertApplication(long candidateId, long offerId, String status, Instant createdAt, Instant viewedAt) {
        jdbc.update("""
                INSERT INTO applications (candidate_id, job_offer_id, status_id, created_at, updated_at, viewed_at)
                VALUES (?, ?, (SELECT id FROM application_statuses WHERE code = ?), ?, ?, ?)""",
                candidateId, offerId, status, ts(createdAt), ts(createdAt), viewedAt == null ? null : ts(viewedAt));
    }

    private void insertLike(long candidateId, long offerId, Instant createdAt) {
        jdbc.update("INSERT INTO job_swipes (candidate_id, job_offer_id, action, created_at) VALUES (?, ?, 'LIKE', ?)",
                candidateId, offerId, ts(createdAt));
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
