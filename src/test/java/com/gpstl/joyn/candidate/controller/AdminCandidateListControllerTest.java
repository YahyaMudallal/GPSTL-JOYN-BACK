package com.gpstl.joyn.candidate.controller;

import com.gpstl.joyn.config.SecurityConfig;
import com.gpstl.joyn.joboffer.JobDatabaseTestSupport;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/v1/admin/candidates (A80) avec le vrai SecurityConfig, de vrais tokens JWT signés et une vraie base.
 * Le schéma de test est propre à cette classe, et les données communes ne contiennent aucun profil candidat :
 * chaque liste ne renvoie que les candidats créés ici.
 */
class AdminCandidateListControllerTest extends JobDatabaseTestSupport {
    private static final String SCHEMA = "job_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private static final String ROUTE = "/api/v1/admin/candidates";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) { configureDatabase(registry, SCHEMA); }

    @Value("${joyn.security.jwt.secret}") String secret;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
        candidate("jean@alpha.test", "Jean", "Dupont", "ACTIVE", "2026-01-01");
        candidate("claire@beta.test", "Claire", "Martin", "SUSPENDED", "2026-01-02");
        candidate("zoe@gamma.test", "Zoe", "Bernard", "ACTIVE", "2026-01-03");
    }

    private long candidate(String email, String firstName, String lastName, String status, String createdAt) {
        long user = insert("INSERT INTO users(email, password_hash, role, status, created_at) "
                + "VALUES (?, 'secret-hash', 'CANDIDATE', ?, ?::timestamptz) RETURNING id", email, status, createdAt + " 00:00:00+00");
        insert("INSERT INTO candidate_profiles(user_id, first_name, last_name) VALUES (?, ?, ?) RETURNING id", user, firstName, lastName);
        return user;
    }

    private String token(long userId, String role) {
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(SecurityConfig.secretKey(secret)));
        var claims = JwtClaimsSet.builder().subject(Long.toString(userId)).claim("role", role)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private ResultActions list(String query) throws Exception {
        return mvc.perform(get(ROUTE + query).header("Authorization", token(admin, "ADMIN")));
    }

    private void assertEmails(String query, String... emails) throws Exception {
        list(query).andExpect(status().isOk()).andExpect(jsonPath("$.data[*].email", contains(emails)));
    }

    /** param() transmet la valeur telle quelle : dans l'URL, MockMvc encoderait « % » une seconde fois. */
    private void assertSearch(String search, String... emails) throws Exception {
        mvc.perform(get(ROUTE).param("search", search).header("Authorization", token(admin, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].email", contains(emails)));
    }

    // --- Contrôle d'accès ---

    @Test
    void anonymousCallGets401() throws Exception {
        mvc.perform(get(ROUTE)).andExpect(status().isUnauthorized());
    }

    @Test
    void standardRolesGet403() throws Exception {
        mvc.perform(get(ROUTE).header("Authorization", token(candidate, "CANDIDATE"))).andExpect(status().isForbidden());
        mvc.perform(get(ROUTE).header("Authorization", token(recruiter, "RECRUITER"))).andExpect(status().isForbidden());
    }

    @Test
    void adminAccountIsCheckedAgainstTheDatabase() throws Exception {
        // Token qui annonce ADMIN pour un compte qui ne l'est pas en base.
        mvc.perform(get(ROUTE).header("Authorization", token(candidate, "ADMIN"))).andExpect(status().isForbidden());
        // Compte supprimé.
        mvc.perform(get(ROUTE).header("Authorization", token(Long.MAX_VALUE, "ADMIN"))).andExpect(status().isUnauthorized());
        // Administrateur suspendu dont le token est encore valide.
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", admin);
        list("").andExpect(status().isForbidden());
    }

    // --- Format de réponse ---

    @Test
    void responseHasTheExpectedFieldsAndPagination() throws Exception {
        long user = candidate("full@delta.test", "Full", "Profile", "ACTIVE", "2026-02-01");
        long profile = jdbc.queryForObject("SELECT id FROM candidate_profiles WHERE user_id = ?", Long.class, user);
        jdbc.update("UPDATE candidate_profiles SET photo_url = 'https://cdn.test/a.jpg' WHERE id = ?", profile);
        jdbc.update("UPDATE users SET email_verified_at = '2026-02-02 00:00:00+00', last_login_at = '2026-02-03 00:00:00+00' WHERE id = ?", user);

        list("?search=full@delta.test")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(user))
                .andExpect(jsonPath("$.data[0].candidate_id").value(profile))
                .andExpect(jsonPath("$.data[0].email").value("full@delta.test"))
                .andExpect(jsonPath("$.data[0].first_name").value("Full"))
                .andExpect(jsonPath("$.data[0].last_name").value("Profile"))
                .andExpect(jsonPath("$.data[0].role").value("CANDIDATE"))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data[0].avatar_url").value("https://cdn.test/a.jpg"))
                .andExpect(jsonPath("$.data[0].is_email_verified").value(true))
                .andExpect(jsonPath("$.data[0].created_at").value("2026-02-01T00:00:00Z"))
                .andExpect(jsonPath("$.data[0].last_login_at").value("2026-02-03T00:00:00Z"))
                .andExpect(jsonPath("$.pagination.current_page").value(1))
                .andExpect(jsonPath("$.pagination.limit").value(20))
                .andExpect(jsonPath("$.pagination.total_items").value(1))
                .andExpect(jsonPath("$.pagination.total_pages").value(1));
    }

    @Test
    void absentOptionalValuesAreReturnedAsNullOrFalse() throws Exception {
        list("?search=jean@alpha.test")
                .andExpect(jsonPath("$.data[0].avatar_url").isEmpty())
                .andExpect(jsonPath("$.data[0].is_email_verified").value(false))
                .andExpect(jsonPath("$.data[0].last_login_at").isEmpty());
    }

    @Test
    void sensitiveDataIsNeverExposed() throws Exception {
        String body = list("").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("secret-hash").doesNotContain("password").doesNotContain("token");
    }

    @Test
    void onlyCandidatesAreListed() throws Exception {
        // Le compte « candidate » des données communes n'a pas de profil ; admin et recruteurs ne sont pas candidats.
        list("").andExpect(jsonPath("$.pagination.total_items").value(3));
        // Profil candidat incohérent rattaché à un compte recruteur : exclu par le filtre sur le rôle.
        insert("INSERT INTO candidate_profiles(user_id, first_name, last_name) VALUES (?, 'Not', 'Candidate') RETURNING id", recruiter);
        list("").andExpect(jsonPath("$.pagination.total_items").value(3));
    }

    @Test
    void profileHiddenFromRecruitersStaysVisibleToAdmins() throws Exception {
        jdbc.update("UPDATE candidate_profiles SET is_visible_to_recruiters = FALSE");
        list("").andExpect(jsonPath("$.pagination.total_items").value(3));
    }

    // --- Recherche et filtres ---

    @Test
    void searchIsCaseInsensitiveOnEmailFirstNameAndLastName() throws Exception {
        assertSearch("BETA.TEST", "claire@beta.test");
        assertSearch("jEaN", "jean@alpha.test");
        assertSearch("bernard", "zoe@gamma.test");
        assertSearch(" dupont ", "jean@alpha.test");
    }

    @Test
    void likeWildcardsAreSearchedLiterally() throws Exception {
        candidate("under_score@delta.test", "Pour%cent", "Back\\slash", "ACTIVE", "2026-02-01");
        assertSearch("%", "under_score@delta.test");
        assertSearch("_", "under_score@delta.test");
        assertSearch("\\", "under_score@delta.test");
    }

    @Test
    void statusFilterIgnoresCaseAndCombinesWithSearch() throws Exception {
        assertEmails("?status=suspended", "claire@beta.test");
        assertEmails("?status=ACTIVE&sort_by=email&order=asc", "jean@alpha.test", "zoe@gamma.test");
        list("?status=ACTIVE&search=claire").andExpect(status().isOk()).andExpect(jsonPath("$.data", empty()));
    }

    @Test
    void emailVerifiedFilter() throws Exception {
        jdbc.update("UPDATE users SET email_verified_at = NOW() WHERE email = 'jean@alpha.test'");
        assertEmails("?email_verified=true", "jean@alpha.test");
        assertEmails("?email_verified=false", "zoe@gamma.test", "claire@beta.test");
    }

    @Test
    void createdDatesAreInclusiveAndCoverTheWholeDay() throws Exception {
        jdbc.update("UPDATE users SET created_at = '2026-01-02 23:59:59+00' WHERE email = 'claire@beta.test'");
        assertEmails("?created_from=2026-01-02", "zoe@gamma.test", "claire@beta.test");
        assertEmails("?created_to=2026-01-02", "claire@beta.test", "jean@alpha.test");
        assertEmails("?created_from=2026-01-02&created_to=2026-01-02", "claire@beta.test");
    }

    @Test
    void lastLoginBeforeExcludesThatDayAndNeverConnectedAccounts() throws Exception {
        jdbc.update("UPDATE users SET last_login_at = '2026-05-31 23:59:59+00' WHERE email = 'jean@alpha.test'");
        jdbc.update("UPDATE users SET last_login_at = '2026-06-01 00:00:00+00' WHERE email = 'claire@beta.test'");
        assertEmails("?last_login_before=2026-06-01", "jean@alpha.test");
    }

    @Test
    void locationFilterIgnoresCaseAndMatchesPartOfTheText() throws Exception {
        jdbc.update("UPDATE candidate_profiles SET location = 'Paris 15e' WHERE first_name = 'Jean'");
        jdbc.update("UPDATE candidate_profiles SET location = 'Lyon' WHERE first_name = 'Claire'");
        assertEmails("?location=paris", "jean@alpha.test");
        mvc.perform(get(ROUTE).param("location", "%").header("Authorization", token(admin, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", empty()));
    }

    @Test
    void visibleToRecruitersFilter() throws Exception {
        jdbc.update("UPDATE candidate_profiles SET is_visible_to_recruiters = FALSE WHERE first_name = 'Jean'");
        assertEmails("?visible_to_recruiters=false", "jean@alpha.test");
        assertEmails("?visible_to_recruiters=true", "zoe@gamma.test", "claire@beta.test");
    }

    @Test
    void hasResumeFilter() throws Exception {
        jdbc.update("UPDATE candidate_profiles SET resume_url = '/cv/jean.pdf' WHERE first_name = 'Jean'");
        assertEmails("?has_resume=true", "jean@alpha.test");
        assertEmails("?has_resume=false", "zoe@gamma.test", "claire@beta.test");
    }

    @Test
    void filtersCombineWithEachOtherAndWithTheSearch() throws Exception {
        jdbc.update("UPDATE candidate_profiles SET location = 'Paris', resume_url = '/cv/a.pdf' WHERE first_name IN ('Jean', 'Zoe')");
        assertEmails("?location=paris&has_resume=true&order=asc", "jean@alpha.test", "zoe@gamma.test");
        assertEmails("?location=paris&has_resume=true&created_from=2026-01-02", "zoe@gamma.test");
        assertEmails("?location=paris&has_resume=true&search=jean", "jean@alpha.test");
        list("?location=paris&has_resume=false").andExpect(jsonPath("$.data", empty()));
    }

    @Test
    void paginationCountsOnlyFilteredCandidates() throws Exception {
        jdbc.update("UPDATE candidate_profiles SET is_visible_to_recruiters = FALSE WHERE first_name IN ('Jean', 'Zoe')");
        list("?visible_to_recruiters=false&limit=1")
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.pagination.total_items").value(2))
                .andExpect(jsonPath("$.pagination.total_pages").value(2));
    }

    @Test
    void blankFiltersAreIgnored() throws Exception {
        list("?location=&status=&search=").andExpect(jsonPath("$.pagination.total_items").value(3));
    }

    // --- Tri ---

    @Test
    void defaultSortIsNewestFirst() throws Exception {
        assertEmails("", "zoe@gamma.test", "claire@beta.test", "jean@alpha.test");
        assertEmails("?order=asc", "jean@alpha.test", "claire@beta.test", "zoe@gamma.test");
    }

    @Test
    void everySortFieldWorks() throws Exception {
        assertEmails("?sort_by=email&order=asc", "claire@beta.test", "jean@alpha.test", "zoe@gamma.test");
        assertEmails("?sort_by=first_name&order=desc", "zoe@gamma.test", "jean@alpha.test", "claire@beta.test");
        assertEmails("?sort_by=last_name&order=asc", "zoe@gamma.test", "jean@alpha.test", "claire@beta.test");
    }

    @Test
    void equalSortValuesKeepAStableOrder() throws Exception {
        candidate("tie1@delta.test", "Same", "Name", "ACTIVE", "2026-03-01");
        candidate("tie2@delta.test", "Same", "Name", "ACTIVE", "2026-03-01");
        candidate("tie3@delta.test", "Same", "Name", "ACTIVE", "2026-03-01");
        assertEmails("?search=delta.test&order=asc&limit=2&page=1", "tie1@delta.test", "tie2@delta.test");
        assertEmails("?search=delta.test&order=asc&limit=2&page=2", "tie3@delta.test");
    }

    // --- Pagination ---

    @Test
    void paginationIsAppliedByPage() throws Exception {
        list("?limit=2&page=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].email", contains("jean@alpha.test")))
                .andExpect(jsonPath("$.pagination.current_page").value(2))
                .andExpect(jsonPath("$.pagination.limit").value(2))
                .andExpect(jsonPath("$.pagination.total_items").value(3))
                .andExpect(jsonPath("$.pagination.total_pages").value(2));
    }

    @Test
    void maximumLimitIsAccepted() throws Exception {
        list("?limit=100").andExpect(status().isOk());
    }

    @Test
    void noResultGivesAnEmptyPage() throws Exception {
        list("?search=nobody-here")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", empty()))
                .andExpect(jsonPath("$.pagination.total_items").value(0))
                .andExpect(jsonPath("$.pagination.total_pages").value(0));
    }

    // --- Validation ---

    @Test
    void invalidParametersGet400WithTheirName() throws Exception {
        record Case(String query, String field) {}
        for (Case invalid : List.of(
                new Case("?page=0", "page"),
                new Case("?page=-1", "page"),
                new Case("?page=abc", "page"),
                new Case("?page=2147483647&limit=100", "page"),
                new Case("?limit=0", "limit"),
                new Case("?limit=101", "limit"),
                new Case("?limit=abc", "limit"),
                new Case("?status=BANNED", "status"),
                new Case("?status=PENDING", "status"),
                new Case("?sort_by=password_hash", "sort_by"),
                new Case("?order=sideways", "order"),
                new Case("?email_verified=maybe", "email_verified"),
                new Case("?visible_to_recruiters=maybe", "visible_to_recruiters"),
                new Case("?has_resume=maybe", "has_resume"),
                new Case("?created_from=hier", "created_from"),
                new Case("?created_to=2026-13-01", "created_to"),
                new Case("?last_login_before=01/02/2026", "last_login_before"),
                new Case("?created_from=2026-02-01&created_to=2026-01-01", "created_to"))) {
            list(invalid.query())
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.errors[0].field").value(invalid.field()));
        }
    }

    @Test
    void typeErrorsExplainTheExpectedFormat() throws Exception {
        list("?has_resume=maybe").andExpect(jsonPath("$.errors[0].message").value("Doit valoir true ou false"));
        list("?created_from=hier").andExpect(jsonPath("$.errors[0].message").value("Doit être une date au format AAAA-MM-JJ"));
        list("?page=abc").andExpect(jsonPath("$.errors[0].message").value("Doit être un nombre entier"));
    }

    @Test
    void dateOutsideTheDatabaseRangeGets400() throws Exception {
        mvc.perform(get(ROUTE).param("created_from", "+999999-01-01").header("Authorization", token(admin, "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("created_from"));
    }

    // --- Base de données ---

    @Test
    void indexesExist() {
        List<String> indexes = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = current_schema() AND tablename = 'users'", String.class);
        assertThat(indexes).contains("idx_users_role_status", "idx_users_created_at");
    }
}
