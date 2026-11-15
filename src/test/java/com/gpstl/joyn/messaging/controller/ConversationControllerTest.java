package com.gpstl.joyn.messaging.controller;

import com.gpstl.joyn.config.SecurityConfig;
import com.gpstl.joyn.messaging.event.MatchCreated;
import com.gpstl.joyn.messaging.service.MessagingStreamService;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'intégration de la messagerie (CO26), sur la vraie base PostgreSQL et avec de vrais tokens.
 *
 * Données créées avant chaque test :
 *  - entreprise "Acme" : recruteuses Rita et Rob, offre "Dev Java" ;
 *  - entreprise "Rival" : recruteur Ravi, offre "Rival job" ;
 *  - Lina a postulé à Dev Java (statut INTERVIEW) et à Rival job (PENDING) ; elle masque son nom de famille ;
 *  - Omar a postulé à Dev Java (statut PENDING).
 * Aucune conversation au départ. Tout est supprimé après chaque test (suffixe unique "run").
 */
@SpringBootTest
class ConversationControllerTest {

    private static final String URL = "/api/v1/conversations";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MessagingStreamService streamService;

    @Autowired
    private ApplicationEventPublisher events;

    @Value("${joyn.security.jwt.secret}")
    private String jwtSecret;

    private MockMvc mockMvc;

    private final String run = UUID.randomUUID().toString().substring(0, 8);
    private long acmeId;
    private long rivalId;
    private long ritaUserId;
    private long robUserId;
    private long raviUserId;
    private long linaUserId;
    private long omarUserId;
    private long omarCandidateId;
    private long linaApplicationId;
    private long omarApplicationId;
    private long devJavaOfferId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        acmeId = id("INSERT INTO companies (name) VALUES (?) RETURNING id", "Acme " + run);
        rivalId = id("INSERT INTO companies (name) VALUES (?) RETURNING id", "Rival " + run);

        ritaUserId = insertUser("rita", "RECRUITER");
        long rita = insertRecruiter(ritaUserId, acmeId, "Rita");
        robUserId = insertUser("rob", "RECRUITER");
        insertRecruiter(robUserId, acmeId, "Rob");
        raviUserId = insertUser("ravi", "RECRUITER");
        long ravi = insertRecruiter(raviUserId, rivalId, "Ravi");

        linaUserId = insertUser("lina", "CANDIDATE");
        long lina = id("""
                INSERT INTO candidate_profiles (user_id, first_name, last_name, show_last_name)
                VALUES (?, 'Lina', 'Test', FALSE) RETURNING id""", linaUserId);
        omarUserId = insertUser("omar", "CANDIDATE");
        omarCandidateId = id("""
                INSERT INTO candidate_profiles (user_id, first_name, last_name)
                VALUES (?, 'Omar', 'Test') RETURNING id""", omarUserId);

        long devJava = insertOffer(acmeId, rita, "Dev Java " + run);
        devJavaOfferId = devJava;
        long rivalJob = insertOffer(rivalId, ravi, "Rival job " + run);

        linaApplicationId = insertApplication(lina, devJava, "INTERVIEW");
        omarApplicationId = insertApplication(omarCandidateId, devJava, "PENDING");
        insertApplication(lina, rivalJob, "PENDING");
    }

    @AfterEach
    void tearDown() {
        // Les conversations et les messages partent avec les candidatures (ON DELETE CASCADE).
        jdbc.update("""
                DELETE FROM applications
                WHERE job_offer_id IN (SELECT id FROM job_offers WHERE company_id IN (?, ?))""", acmeId, rivalId);
        jdbc.update("DELETE FROM job_offers WHERE company_id IN (?, ?)", acmeId, rivalId);
        jdbc.update("DELETE FROM users WHERE email LIKE ?", "%-" + run + "@messaging.test");
        jdbc.update("DELETE FROM companies WHERE id IN (?, ?)", acmeId, rivalId);
    }

    // ================================================================ sécurité

    @Test
    void rejectsRequestWithoutTokenOrWithAdminRole() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL).header("Authorization", bearer(1, "ADMIN"))).andExpect(status().isForbidden());
    }

    // ================================================================ POST /conversations

    @Test
    void recruiterOpensConversationOnceThenGetsTheSameOne() throws Exception {
        String json = open(ritaUserId, "{\"application_id\": " + omarApplicationId + "}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applicationId").value(omarApplicationId))
                .andExpect(jsonPath("$.name").value("Omar Test"))
                .andExpect(jsonPath("$.offerTitle").value("Dev Java " + run))
                .andExpect(jsonPath("$.companyName").value("Acme " + run))
                .andExpect(jsonPath("$.lastMessage").value(nullValue()))
                .andExpect(jsonPath("$.unread").value(0))
                .andReturn().getResponse().getContentAsString();
        Number firstId = JsonPath.read(json, "$.id");

        // Deuxième appel : pas de doublon, 200 et la même conversation.
        open(ritaUserId, "{\"applicationId\": " + omarApplicationId + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstId.longValue()));
    }

    @Test
    void recruiterCanOpenWithCandidateIdSentAsText() throws Exception {
        // Format actuel du front recruteur : {"candidateId": "7"}.
        open(ritaUserId, "{\"candidateId\": \"" + omarCandidateId + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applicationId").value(omarApplicationId));
    }

    @Test
    void candidateCanOpenOnlyAtInterviewStep() throws Exception {
        open(omarUserId, "{\"application_id\": " + omarApplicationId + "}")
                .andExpect(status().isForbidden());

        open(linaUserId, "{\"application_id\": " + linaApplicationId + "}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Acme " + run));
    }

    @Test
    void rejectsOtherCompanyUnknownOrInvalidApplication() throws Exception {
        open(raviUserId, "{\"application_id\": " + omarApplicationId + "}").andExpect(status().isForbidden());
        open(omarUserId, "{\"application_id\": " + linaApplicationId + "}").andExpect(status().isForbidden());

        open(ritaUserId, "{\"application_id\": 999999999}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].field").value("application_id"));
        open(ritaUserId, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("application_id"));
        open(ritaUserId, "{\"application_id\": \"abc\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("application_id"));
    }

    // ================================================================ envoi et liste

    @Test
    void sentMessageAppearsInBothSidesWithUnreadCount() throws Exception {
        long conversationId = openAs(ritaUserId, linaApplicationId);

        send(ritaUserId, conversationId, "{\"text\": \"Bonjour Lina\", \"clientId\": \"tmp-1\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.text").value("Bonjour Lina"))
                .andExpect(jsonPath("$.fromMe").value(true))
                .andExpect(jsonPath("$.senderRole").value("RECRUITER"))
                .andExpect(jsonPath("$.senderName").value("Rita"))
                .andExpect(jsonPath("$.clientId").value("tmp-1"))
                .andExpect(jsonPath("$.createdAt").value(notNullValue()))
                .andExpect(jsonPath("$.readAt").value(nullValue()));

        // Côté candidate : le nom affiché est celui de l'entreprise, 1 message non lu.
        mockMvc.perform(as(linaUserId, "CANDIDATE", get(URL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(conversationId))
                .andExpect(jsonPath("$.data[0].name").value("Acme " + run))
                .andExpect(jsonPath("$.data[0].lastMessage").value("Bonjour Lina"))
                .andExpect(jsonPath("$.data[0].unread").value(1))
                .andExpect(jsonPath("$.totalUnread").value(1));

        // Côté recruteuse : le nom affiché est celui de la candidate (nom de famille masqué), 0 non lu.
        mockMvc.perform(as(ritaUserId, "RECRUITER", get(URL)))
                .andExpect(jsonPath("$.data[0].name").value("Lina T."))
                .andExpect(jsonPath("$.data[0].unread").value(0));
    }

    @Test
    void colleagueOfTheSameCompanyCanReadAndReply() throws Exception {
        long conversationId = openAs(ritaUserId, linaApplicationId);
        send(ritaUserId, conversationId, "{\"text\": \"Bonjour\"}").andExpect(status().isCreated());

        mockMvc.perform(as(robUserId, "RECRUITER", get(URL + "/" + conversationId + "/messages")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].fromMe").value(false));
        send(robUserId, conversationId, "{\"content\": \"Je complète\"}").andExpect(status().isCreated());

        // Le message de Rob n'est pas "non lu" pour Rita : seuls ceux de la candidate comptent.
        mockMvc.perform(as(ritaUserId, "RECRUITER", get(URL + "/" + conversationId)))
                .andExpect(jsonPath("$.unread").value(0))
                .andExpect(jsonPath("$.lastMessage").value("Je complète"));
    }

    @Test
    void outsidersCannotSeeOrWriteInAConversation() throws Exception {
        long conversationId = openAs(ritaUserId, linaApplicationId);

        mockMvc.perform(as(raviUserId, "RECRUITER", get(URL + "/" + conversationId + "/messages")))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(omarUserId, "CANDIDATE", get(URL + "/" + conversationId)))
                .andExpect(status().isForbidden());
        send(omarUserId, conversationId, "{\"text\": \"Coucou\"}").andExpect(status().isForbidden());
        mockMvc.perform(as(omarUserId, "CANDIDATE", patch(URL + "/" + conversationId + "/read")))
                .andExpect(status().isForbidden());

        // Et la conversation n'apparaît pas dans leurs listes.
        mockMvc.perform(as(raviUserId, "RECRUITER", get(URL))).andExpect(jsonPath("$.data", hasSize(0)));
        mockMvc.perform(as(omarUserId, "CANDIDATE", get(URL))).andExpect(jsonPath("$.data", hasSize(0)));

        mockMvc.perform(as(ritaUserId, "RECRUITER", get(URL + "/999999999/messages")))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(ritaUserId, "RECRUITER", get(URL + "/abc/messages")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletedCompanyMakesConversationsReadOnly() throws Exception {
        long conversationId = openAs(ritaUserId, linaApplicationId);
        send(ritaUserId, conversationId, "{\"text\": \"Bonjour Lina\"}").andExpect(status().isCreated());

        jdbc.update("UPDATE companies SET status = 'DELETED', deleted_at = NOW() WHERE id = ?", acmeId);

        // Recruteurs de l'entreprise supprimée : plus aucun accès à la messagerie.
        mockMvc.perform(as(ritaUserId, "RECRUITER", get(URL))).andExpect(status().isForbidden());
        mockMvc.perform(as(robUserId, "RECRUITER", get(URL + "/" + conversationId + "/messages")))
                .andExpect(status().isForbidden());
        send(ritaUserId, conversationId, "{\"text\": \"Encore là\"}").andExpect(status().isForbidden());
        open(ritaUserId, "{\"application_id\": " + omarApplicationId + "}").andExpect(status().isForbidden());

        // Candidate : l'historique reste lisible, mais elle ne peut plus écrire.
        mockMvc.perform(as(linaUserId, "CANDIDATE", get(URL + "/" + conversationId + "/messages")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
        send(linaUserId, conversationId, "{\"text\": \"Bonjour ?\"}").andExpect(status().isForbidden());
        // La conversation existante est renvoyée telle quelle
        open(linaUserId, "{\"application_id\": " + linaApplicationId + "}").andExpect(status().isOk());

        // Les autres entreprises ne sont pas touchées.
        mockMvc.perform(as(raviUserId, "RECRUITER", get(URL))).andExpect(status().isOk());
    }

    @Test
    void candidateCannotOpenConversationWithDeletedCompany() throws Exception {
        jdbc.update("UPDATE companies SET status = 'DELETED', deleted_at = NOW() WHERE id = ?", acmeId);

        open(linaUserId, "{\"application_id\": " + linaApplicationId + "}").andExpect(status().isForbidden());
    }

    @Test
    void rejectsEmptyOrTooLongMessages() throws Exception {
        long conversationId = openAs(ritaUserId, linaApplicationId);

        send(ritaUserId, conversationId, "{\"text\": \"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("text"));
        send(ritaUserId, conversationId, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("text"));
        send(ritaUserId, conversationId, "{\"text\": \"" + "a".repeat(5001) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("text"));
        send(ritaUserId, conversationId, "{\"text\": 12}")
                .andExpect(status().isBadRequest());
    }

    // ================================================================ pagination par curseur

    @Test
    void paginatesMessagesWithACursor() throws Exception {
        long conversationId = openAs(ritaUserId, linaApplicationId);
        for (int i = 1; i <= 5; i++) {
            long sender = i % 2 == 1 ? ritaUserId : linaUserId;
            send(sender, conversationId, "{\"text\": \"m" + i + "\"}").andExpect(status().isCreated());
        }
        String messages = URL + "/" + conversationId + "/messages";

        // Page 1 : les 2 plus récents, du plus ancien au plus récent (prêt à afficher).
        String page1 = mockMvc.perform(as(linaUserId, "CANDIDATE", get(messages).param("limit", "2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].text", contains("m4", "m5")))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn().getResponse().getContentAsString();
        Number cursor = JsonPath.read(page1, "$.nextCursor");

        // Page 2 : les 2 d'avant.
        String page2 = mockMvc.perform(as(linaUserId, "CANDIDATE",
                        get(messages).param("limit", "2").param("before", String.valueOf(cursor))))
                .andExpect(jsonPath("$.data[*].text", contains("m2", "m3")))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andReturn().getResponse().getContentAsString();
        Number cursor2 = JsonPath.read(page2, "$.nextCursor");

        // Page 3 : le dernier, plus rien après.
        mockMvc.perform(as(linaUserId, "CANDIDATE",
                        get(messages).param("limit", "2").param("before", String.valueOf(cursor2))))
                .andExpect(jsonPath("$.data[*].text", contains("m1")))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));

        // Ordre antéchronologique sur demande.
        mockMvc.perform(as(linaUserId, "CANDIDATE", get(messages).param("order", "desc")))
                .andExpect(jsonPath("$.data[*].text", contains("m5", "m4", "m3", "m2", "m1")))
                .andExpect(jsonPath("$.data[0].fromMe").value(false))
                .andExpect(jsonPath("$.data[1].fromMe").value(true));

        mockMvc.perform(as(linaUserId, "CANDIDATE", get(messages).param("limit", "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("limit"));
        mockMvc.perform(as(linaUserId, "CANDIDATE", get(messages).param("order", "up")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("order"));
    }

    // ================================================================ lu / non lu

    @Test
    void markAsReadClearsTheUnreadCount() throws Exception {
        long conversationId = openAs(ritaUserId, linaApplicationId);
        send(ritaUserId, conversationId, "{\"text\": \"Un\"}");
        send(ritaUserId, conversationId, "{\"text\": \"Deux\"}");
        send(linaUserId, conversationId, "{\"text\": \"Ma réponse\"}");

        mockMvc.perform(as(linaUserId, "CANDIDATE", patch(URL + "/" + conversationId + "/read")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"read\": true}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markedAsRead").value(2))
                .andExpect(jsonPath("$.unread").value(0));

        mockMvc.perform(as(linaUserId, "CANDIDATE", get(URL)))
                .andExpect(jsonPath("$.data[0].unread").value(0))
                .andExpect(jsonPath("$.totalUnread").value(0));
        // Le message de Lina, lui, n'est pas encore lu par l'entreprise.
        mockMvc.perform(as(ritaUserId, "RECRUITER", get(URL)))
                .andExpect(jsonPath("$.data[0].unread").value(1));
        mockMvc.perform(as(ritaUserId, "RECRUITER", get(URL + "/" + conversationId + "/messages")))
                .andExpect(jsonPath("$.data[0].readAt").value(notNullValue()))
                .andExpect(jsonPath("$.data[2].readAt").value(nullValue()));
    }

    // ================================================================ ouverture automatique

    @Test
    void conversationOpensAutomaticallyWhenApplicationMovesToInterview() throws Exception {
        mockMvc.perform(as(ritaUserId, "RECRUITER", patch("/api/v1/applications/" + omarApplicationId + "/status")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"INTERVIEW\"}")))
                .andExpect(status().isOk());

        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM conversations WHERE application_id = ?",
                Long.class, omarApplicationId);
        assertEquals(1L, count);
        mockMvc.perform(as(omarUserId, "CANDIDATE", get(URL)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].applicationId").value(omarApplicationId));
    }

    @Test
    void otherStatusesDoNotOpenAConversation() throws Exception {
        mockMvc.perform(as(ritaUserId, "RECRUITER", patch("/api/v1/applications/" + omarApplicationId + "/status")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"IN_REVIEW\"}")))
                .andExpect(status().isOk());
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM conversations WHERE application_id = ?",
                Long.class, omarApplicationId);
        assertEquals(0L, count);
    }

    @Test
    void conversationOpensAutomaticallyWhenAMatchIsCreated() throws Exception {
        // Ce que fera le futur module de matching : créer le match, puis publier MatchCreated.
        long matchId = id("""
                INSERT INTO matches (candidate_id, job_offer_id, compatibility_score, status)
                VALUES (?, ?, 87, 'ACTIVE') RETURNING id""", omarCandidateId, devJavaOfferId);
        events.publishEvent(new MatchCreated(matchId));
        events.publishEvent(new MatchCreated(matchId)); // une deuxième fois : pas de doublon

        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM conversations WHERE match_id = ?", Long.class, matchId);
        assertEquals(1L, count);

        String json = mockMvc.perform(as(omarUserId, "CANDIDATE", get(URL)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].matchId").value(matchId))
                .andExpect(jsonPath("$.data[0].applicationId").value(nullValue()))
                .andExpect(jsonPath("$.data[0].name").value("Acme " + run))
                .andReturn().getResponse().getContentAsString();
        Number conversationId = JsonPath.read(json, "$.data[0].id");

        // Les deux côtés peuvent écrire dans une conversation née d'un match.
        send(ritaUserId, conversationId.longValue(), "{\"text\": \"Ravie de ce match !\"}")
                .andExpect(status().isCreated());
        mockMvc.perform(as(omarUserId, "CANDIDATE", get(URL)))
                .andExpect(jsonPath("$.data[0].unread").value(1));
    }

    // ================================================================ temps réel (SSE)

    @Test
    void realTimeStreamOpensForAParticipant() throws Exception {
        mockMvc.perform(as(linaUserId, "CANDIDATE", get(URL + "/stream").accept(MediaType.TEXT_EVENT_STREAM)))
                .andExpect(request().asyncStarted());
        assertTrue(streamService.connectionCount(linaUserId) >= 1);

        mockMvc.perform(get(URL + "/stream")).andExpect(status().isUnauthorized());
    }

    // ================================================================ outils

    private ResultActions open(long userId, String body) throws Exception {
        String role = userId == linaUserId || userId == omarUserId ? "CANDIDATE" : "RECRUITER";
        return mockMvc.perform(as(userId, role, post(URL)
                .contentType(MediaType.APPLICATION_JSON).content(body)));
    }

    private long openAs(long userId, long applicationId) throws Exception {
        String json = open(userId, "{\"application_id\": " + applicationId + "}")
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(json, "$.id");
        return id.longValue();
    }

    private ResultActions send(long userId, long conversationId, String body) throws Exception {
        String role = userId == linaUserId || userId == omarUserId ? "CANDIDATE" : "RECRUITER";
        return mockMvc.perform(as(userId, role, post(URL + "/" + conversationId + "/messages")
                .contentType(MediaType.APPLICATION_JSON).content(body)));
    }

    private MockHttpServletRequestBuilder as(long userId, String role, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", bearer(userId, role));
    }

    private long insertUser(String name, String role) {
        return id("INSERT INTO users (email, password_hash, role) VALUES (?, 'not-a-real-hash', ?) RETURNING id",
                name + "-" + run + "@messaging.test", role);
    }

    private long insertRecruiter(long userId, long companyId, String firstName) {
        return id("""
                INSERT INTO recruiter_profiles (user_id, company_id, first_name, last_name)
                VALUES (?, ?, ?, 'Test') RETURNING id""", userId, companyId, firstName);
    }

    private long insertOffer(long companyId, long recruiterId, String title) {
        return id("""
                INSERT INTO job_offers (company_id, recruiter_id, contract_type_id, title, description, status, published_at)
                VALUES (?, ?, (SELECT id FROM contract_types WHERE code = 'APPRENTICESHIP'), ?, 'Description', 'PUBLISHED', NOW())
                RETURNING id""", companyId, recruiterId, title);
    }

    private long insertApplication(long candidateId, long offerId, String status) {
        return id("""
                INSERT INTO applications (candidate_id, job_offer_id, status_id)
                VALUES (?, ?, (SELECT id FROM application_statuses WHERE code = ?))
                RETURNING id""", candidateId, offerId, status);
    }

    private long id(String sql, Object... args) {
        Long id = jdbc.queryForObject(sql, Long.class, args);
        return id == null ? 0 : id;
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
