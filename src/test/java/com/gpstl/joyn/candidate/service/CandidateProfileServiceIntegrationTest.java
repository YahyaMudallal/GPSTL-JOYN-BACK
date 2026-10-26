package com.gpstl.joyn.candidate.service;

import com.gpstl.joyn.candidate.dto.CandidateProfileResponse;
import com.gpstl.joyn.candidate.event.CandidateMatchingCriteriaChanged;
import com.gpstl.joyn.candidate.exception.CandidateProfileException;
import com.gpstl.joyn.candidate.repository.CandidateRepository;
import com.gpstl.joyn.joboffer.JobDatabaseTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Service du profil candidat sur une vraie base PostgreSQL (schéma temporaire).
 * Les données sont préparées et vérifiées en SQL, indépendamment du code JPA testé.
 */
@RecordApplicationEvents
class CandidateProfileServiceIntegrationTest extends JobDatabaseTestSupport {
    private static final String SCHEMA = "job_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) { configureDatabase(registry, SCHEMA); }

    @Autowired CandidateProfileService service;
    @Autowired CandidateRepository candidates;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManager entityManager;
    @Autowired ApplicationEvents events;
    private final JsonMapper json = JsonMapper.builder().build();
    private long profile;
    private long go;

    @BeforeEach
    void profile() {
        // updated_at d'hier : on peut vérifier que le trigger le remet à jour.
        profile = insert("""
            INSERT INTO candidate_profiles(user_id, first_name, last_name, bio, updated_at)
            VALUES (?, 'Old', 'Name', 'Old bio', NOW() - INTERVAL '1 day') RETURNING id""", candidate);
        go = insert("INSERT INTO skills(name) VALUES (?) RETURNING id", "Go-" + UUID.randomUUID());
        jdbc.update("INSERT INTO candidate_skills(candidate_id, skill_id, level) VALUES (?, ?, 'ADVANCED')", profile, go);
        jdbc.update("INSERT INTO candidate_experiences(candidate_id, title) VALUES (?, 'E1'), (?, 'E2')", profile, profile);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(String content) {
        return json.readValue(content, Map.class);
    }

    private String goName() {
        return jdbc.queryForObject("SELECT name FROM skills WHERE id = ?", String.class, go);
    }

    private int status(Runnable action) {
        var error = catchThrowableOfType(CandidateProfileException.class, action::run);
        assertThat(error).isNotNull();
        return error.getStatus();
    }

    private boolean updatedRecently() {
        return jdbc.queryForObject("SELECT updated_at > NOW() - INTERVAL '1 hour' FROM candidate_profiles WHERE id = ?",
                Boolean.class, profile);
    }

    @Test
    void getAggregatesProfileAndRelatedSections() {
        CandidateProfileResponse profile = service.getProfile(candidate);
        assertThat(profile.firstName()).isEqualTo("Old");
        assertThat(profile.email()).endsWith("@example.test");
        assertThat(profile.skills()).containsExactly(goName());
        assertThat(profile.experiences()).extracting(CandidateProfileResponse.Experience::title).containsExactly("E1", "E2");
        assertThat(profile.preferences().currency()).isEqualTo("EUR");
    }

    @Test
    void patchUpdatesOnlySuppliedFieldsAndSynchronizesSections() {
        var response = service.updateProfile(candidate, body("""
            {"first_name":"Yahya","headline":"Ingénieur","phone":"+33612345678","years_of_experience":2,
             "skills":["%s","Python","Docker"],
             "preferences":{"contract_types":["CDI","FREELANCE"],"remote_policy":"HYBRID","min_salary":48000},
             "experiences":[{"title":"Stage SRE","start_date":"2025-06-01"}]}""".formatted(goName().toLowerCase())));

        assertThat(response.firstName()).isEqualTo("Yahya");
        assertThat(response.lastName()).isEqualTo("Name");      // absent : conservé
        assertThat(response.bio()).isEqualTo("Old bio");
        assertThat(response.skills()).containsExactlyInAnyOrder("Docker", goName(), "Python");
        assertThat(response.preferences().contractTypes()).containsExactly("CDI", "FREELANCE");
        assertThat(response.preferences().minSalary()).isEqualTo(48000L);
        assertThat(response.experiences()).extracting(CandidateProfileResponse.Experience::title).containsExactly("Stage SRE");

        assertThat(jdbc.queryForObject("SELECT phone FROM candidate_profiles WHERE id = ?", String.class, profile)).isEqualTo("+33612345678");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM candidate_preferred_contract_types WHERE candidate_id = ?", Integer.class, profile)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM candidate_experiences WHERE candidate_id = ?", Integer.class, profile)).isEqualTo(1);
    }

    @Test
    void keptSkillsKeepTheirLevelAndNewOnesUseTheDefault() {
        // « go » en minuscules correspond à la compétence existante (index unique LOWER(name)).
        service.updateProfile(candidate, body("{\"skills\":[\"%s\",\"Rust-%s\"]}".formatted(goName().toLowerCase(), UUID.randomUUID())));
        assertThat(jdbc.queryForObject("SELECT level FROM candidate_skills WHERE candidate_id = ? AND skill_id = ?",
                String.class, profile, go)).isEqualTo("ADVANCED");
        assertThat(jdbc.queryForList("SELECT level FROM candidate_skills WHERE candidate_id = ? AND skill_id <> ?",
                String.class, profile, go)).containsExactly("INTERMEDIATE");
        // Le catalogue partagé n'est jamais vidé par la synchronisation.
        service.updateProfile(candidate, body("{\"skills\":[]}"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM candidate_skills WHERE candidate_id = ?", Integer.class, profile)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM skills WHERE id = ?", Integer.class, go)).isEqualTo(1);
    }

    @Test
    void responseReflectsTheTimestampSetByTheDatabaseTrigger() {
        var response = service.updateProfile(candidate, body("{\"bio\":\"New\"}"));
        assertThat(updatedRecently()).isTrue();
        assertThat(response.updatedAt()).isEqualTo(
                jdbc.queryForObject("SELECT updated_at FROM candidate_profiles WHERE id = ?", Timestamp.class, profile).toInstant());
    }

    @Test
    void changingOnlyASectionStillUpdatesTheTimestamp() {
        service.updateProfile(candidate, body("{\"educations\":[]}"));
        assertThat(updatedRecently()).isTrue();
    }

    @Test
    void publishesTheMatchingEventOnlyWhenSkillsOrPreferencesChange() {
        service.updateProfile(candidate, body("{\"bio\":\"New\",\"experiences\":[]}"));
        assertThat(events.stream(CandidateMatchingCriteriaChanged.class)).isEmpty();
        service.updateProfile(candidate, body("{\"preferences\":{\"remote_policy\":\"REMOTE\"}}"));
        assertThat(events.stream(CandidateMatchingCriteriaChanged.class))
                .singleElement().extracting(CandidateMatchingCriteriaChanged::candidateId).isEqualTo(profile);
    }

    @Test
    void unknownContractTypesAreRejectedBeforeAnyChange() {
        var error = catchThrowableOfType(CandidateProfileException.class, () ->
                service.updateProfile(candidate, body("{\"bio\":\"New\",\"preferences\":{\"contract_types\":[\"CDI\",\"NOPE\"]}}")));
        assertThat(error.getStatus()).isEqualTo(400);
        assertThat(error.getErrors().get("preferences.contract_types")).contains("[NOPE]").contains("CDI");
        assertThat(jdbc.queryForObject("SELECT bio FROM candidate_profiles WHERE id = ?", String.class, profile)).isEqualTo("Old bio");
    }

    @Test
    void checksTheAccountStoredInTheDatabase() {
        assertThat(status(() -> service.getProfile(recruiter))).isEqualTo(403);
        // Le compte est vérifié avant le corps : un recruteur reçoit 403 même avec un JSON invalide.
        assertThat(status(() -> service.updateProfile(recruiter, body("{\"bio\":5}")))).isEqualTo(403);
        assertThat(status(() -> service.getProfile(999_999_999L))).isEqualTo(401);
    }

    @Test
    void accountWithoutProfileGets404() {
        jdbc.update("DELETE FROM candidate_profiles WHERE id = ?", profile);
        assertThat(status(() -> service.getProfile(candidate))).isEqualTo(404);
        assertThat(status(() -> service.updateProfile(candidate, body("{}")))).isEqualTo(404);
    }

    @Test
    void suspendedAccountGets403() {
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", candidate);
        // Une requête réelle ouvre une nouvelle transaction ; ici on vide le cache Hibernate du test.
        entityManager.clear();
        assertThat(status(() -> service.getProfile(candidate))).isEqualTo(403);
    }

    private void contactDetails() {
        jdbc.update("""
            UPDATE candidate_profiles SET phone = '+33612345678', adress = 'Paris 9e',
                github_url = 'https://github.com/old' WHERE id = ?""", profile);
    }

    @Test
    void recruiterSeesTheFullProfileWhenTheCandidateHidesNothing() {
        contactDetails();
        var seen = service.getProfileById(recruiter, profile);
        assertThat(seen.lastName()).isEqualTo("Name");
        assertThat(seen.email()).endsWith("@example.test");
        assertThat(seen.phone()).isEqualTo("+33612345678");
        assertThat(seen.links().github()).isEqualTo("https://github.com/old");
        assertThat(seen.skills()).containsExactly(goName());
        // Les réglages eux-mêmes restent privés.
        assertThat(seen.visibility()).isNull();
    }

    @Test
    void recruiterViewAppliesTheCandidateVisibilitySettings() {
        contactDetails();
        jdbc.update("UPDATE candidate_profiles SET show_last_name = FALSE, show_contact_links = FALSE WHERE id = ?", profile);
        var seen = service.getProfileById(recruiter, profile);
        assertThat(seen.firstName()).isEqualTo("Old");
        assertThat(seen.lastName()).isEqualTo("N.");
        assertThat(seen.email()).isNull();
        assertThat(seen.phone()).isNull();
        assertThat(seen.address()).isNull();
        assertThat(seen.links().github()).isNull();
        // Le reste du profil est inchangé.
        assertThat(seen.bio()).isEqualTo("Old bio");
        assertThat(seen.experiences()).hasSize(2);
    }

    @Test
    void profileHiddenFromRecruitersOrSuspendedLooksMissing() {
        jdbc.update("UPDATE candidate_profiles SET is_visible_to_recruiters = FALSE WHERE id = ?", profile);
        assertThat(status(() -> service.getProfileById(recruiter, profile))).isEqualTo(404);
        jdbc.update("UPDATE candidate_profiles SET is_visible_to_recruiters = TRUE WHERE id = ?", profile);
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", candidate);
        entityManager.clear();
        assertThat(status(() -> service.getProfileById(recruiter, profile))).isEqualTo(404);
    }

    @Test
    void administratorAlwaysSeesTheFullProfile() {
        contactDetails();
        jdbc.update("""
            UPDATE candidate_profiles SET is_visible_to_recruiters = FALSE, show_last_name = FALSE,
                show_contact_links = FALSE WHERE id = ?""", profile);
        var seen = service.getProfileById(admin, profile);
        assertThat(seen.lastName()).isEqualTo("Name");
        assertThat(seen.phone()).isEqualTo("+33612345678");
        assertThat(seen.visibility().visibleToRecruiters()).isFalse();
    }

    @Test
    void readingByIdIsReservedToActiveRecruitersAndAdministrators() {
        assertThat(status(() -> service.getProfileById(candidate, profile))).isEqualTo(403);
        assertThat(status(() -> service.getProfileById(999_999_999L, profile))).isEqualTo(401);
        assertThat(status(() -> service.getProfileById(recruiter, 999_999_999L))).isEqualTo(404);
        // La route du candidat reste réservée aux candidats.
        assertThat(status(() -> service.getProfile(admin))).isEqualTo(403);
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", recruiter);
        entityManager.clear();
        assertThat(status(() -> service.getProfileById(recruiter, profile))).isEqualTo(403);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentUpdatesOfTheSameProfileRunOneAfterTheOther() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        var transaction = new TransactionTemplate(transactions);
        var second = new AtomicReference<Future<CandidateProfileResponse>>();
        try {
            transaction.executeWithoutResult(status -> {
                // La première transaction garde le verrou du profil pendant l'arrivée de la seconde.
                candidates.findByUserIdForUpdate(candidate).orElseThrow();
                second.set(executor.submit(() -> service.updateProfile(candidate, Map.of("bio", "Second"))));
                assertThatThrownBy(() -> second.get().get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                service.updateProfile(candidate, Map.of("bio", "First", "headline", "Kept"));
            });
            // La seconde mise à jour s'applique après le commit et ne perd pas le champ qu'elle n'a pas envoyé.
            var response = second.get().get(5, TimeUnit.SECONDS);
            assertThat(response.bio()).isEqualTo("Second");
            assertThat(response.headline()).isEqualTo("Kept");
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentCreationOfTheSameNewSkillProducesOneCatalogEntry() throws Exception {
        long otherUser = insert("INSERT INTO users(email, password_hash, role) VALUES (?, 'test-only', 'CANDIDATE') RETURNING id",
                UUID.randomUUID() + "@example.test");
        insert("INSERT INTO candidate_profiles(user_id, first_name, last_name) VALUES (?, 'C', 'D') RETURNING id", otherUser);
        String name = "Kotlin-" + UUID.randomUUID();
        var executor = Executors.newSingleThreadExecutor();
        var transaction = new TransactionTemplate(transactions);
        var second = new AtomicReference<Future<CandidateProfileResponse>>();
        try {
            transaction.executeWithoutResult(status -> {
                service.updateProfile(candidate, Map.of("skills", java.util.List.of(name)));
                // Même nom avec une autre casse : l'INSERT ... ON CONFLICT attend la fin de la première transaction.
                second.set(executor.submit(() -> service.updateProfile(otherUser, Map.of("skills", java.util.List.of(name.toLowerCase())))));
                assertThatThrownBy(() -> second.get().get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            });
            // Pas d'erreur d'unicité : la seconde transaction réutilise la ligne validée par la première.
            assertThat(second.get().get(5, TimeUnit.SECONDS).skills()).containsExactly(name);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM skills WHERE LOWER(name) = LOWER(?)", Integer.class, name)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
