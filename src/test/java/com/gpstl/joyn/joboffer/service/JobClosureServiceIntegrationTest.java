package com.gpstl.joyn.joboffer.service;

import com.gpstl.joyn.joboffer.JobDatabaseTestSupport;
import com.gpstl.joyn.joboffer.exception.JobApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Vérifie la conservation de l’historique, le rollback et le verrouillage sur PostgreSQL. */
class JobClosureServiceIntegrationTest extends JobDatabaseTestSupport {
    private static final String SCHEMA = "job_test_" + UUID.randomUUID().toString().replace("-", "");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) { configureDatabase(registry, SCHEMA); }
    @Autowired JobClosureService service;
    @Autowired JobAccessService access;
    @Autowired PlatformTransactionManager transactions;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void jpaLockSerializesConcurrentClosuresAndAuditsOnlyOnce() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        var started = new CountDownLatch(1);
        var transaction = new TransactionTemplate(transactions);
        var pending = new AtomicReference<Future<Integer>>();
        try {
            transaction.executeWithoutResult(status -> {
                // La première transaction garde le verrou pendant l’arrivée de la seconde requête.
                access.authorizedJob(recruiter, job);
                pending.set(executor.submit(() -> {
                    started.countDown();
                    try {
                        service.close(recruiter, job, "Second request");
                        return 204;
                    } catch (JobApiException ex) {
                        return ex.getStatus();
                    }
                }));
                try {
                    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                // La seconde clôture doit attendre le commit, puis constater la suppression (404).
                assertThatThrownBy(() -> pending.get().get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
                service.close(recruiter, job, "First request");
            });
            assertThat(pending.get().get(5, TimeUnit.SECONDS)).isEqualTo(404);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job_offer_audit WHERE job_offer_id = ?", Integer.class, job)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }


    @Test void closePreservesApplicationsMatchesConversationsAndMessages() {
        long cp = insert("INSERT INTO candidate_profiles(user_id, first_name, last_name) VALUES (?, 'E', 'F') RETURNING id", candidate);
        int pending = jdbc.queryForObject("SELECT id FROM application_statuses WHERE code = 'PENDING'", Integer.class);
        long app = insert("INSERT INTO applications(candidate_id, job_offer_id, status_id) VALUES (?, ?, ?) RETURNING id", cp, job, pending);
        long match = insert("INSERT INTO matches(candidate_id, job_offer_id) VALUES (?, ?) RETURNING id", cp, job);
        long conversation = insert("INSERT INTO conversations(application_id, match_id) VALUES (?, ?) RETURNING id", app, match);
        long message = insert("INSERT INTO messages(conversation_id, sender_user_id, content) VALUES (?, ?, 'Hello') RETURNING id", conversation, candidate);
        service.close(recruiter, job, "Filled");
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL AND status = 'CLOSED' FROM job_offers WHERE id = ?", Boolean.class, job)).isTrue();
        for (var relation : Map.of("applications", app, "matches", match, "conversations", conversation, "messages", message).entrySet())
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + relation.getKey() + " WHERE id = ?", Integer.class, relation.getValue())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job_offers WHERE id = ? AND deleted_at IS NULL AND status = 'PUBLISHED'", Integer.class, job)).isZero();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollsBackClosureIfAuditFails() {
        // Simuler une panne d’audit pour vérifier que l’offre ne reste pas partiellement clôturée.
        jdbc.execute("ALTER TABLE job_offer_audit ADD CONSTRAINT test_close_audit_failure CHECK (action <> 'CLOSE')");
        try {
            assertThatThrownBy(() -> service.close(recruiter, job, "Must rollback"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("SELECT status = 'PUBLISHED' AND deleted_at IS NULL FROM job_offers WHERE id = ?", Boolean.class, job)).isTrue();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job_offer_audit WHERE job_offer_id = ?", Integer.class, job)).isZero();
        } finally {
            jdbc.execute("ALTER TABLE job_offer_audit DROP CONSTRAINT test_close_audit_failure");
        }
    }
    @Test void administratorsAndOwnerCanCloseButOtherAccountsCannot() {
        assertError(403, () -> service.close(outsider, job, null));
        assertError(403, () -> service.close(candidate, job, null));
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", recruiter);
        assertError(403, () -> service.close(recruiter, job, null));
        assertError(401, () -> service.close(Long.MAX_VALUE, job, null));
        service.close(admin, job, "Admin closure");
        assertThat(jdbc.queryForObject("SELECT status FROM job_offers WHERE id = ?", String.class, job)).isEqualTo("CLOSED");
    }
    @Test void missingAndRepeatedDeletesReturn404AndReasonIsAudited() {
        assertError(404, () -> service.close(admin, Long.MAX_VALUE, null));
        service.close(recruiter, job, "Poste pourvu");
        assertError(404, () -> service.close(recruiter, job, null));
        assertThat(jdbc.queryForObject("SELECT reason FROM job_offer_audit WHERE job_offer_id = ?", String.class, job)).isEqualTo("Poste pourvu");
    }


    @Test void existingCreationAndSecurityWorkWithThisEndpoint() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
        mvc.perform(get("/openapi/job-soft-delete.yaml")).andExpect(status().isOk());
        String name = "Shared-" + UUID.randomUUID();
        mvc.perform(post("/api/v1/recruiters/jobs").header("Authorization", bearer(recruiter))
            .contentType("application/json").content("""
                {"title":"New offer", "description":"Created through main's endpoint",
                 "contract_type":"CDI", "location":"Paris", "remote_policy":"HYBRID",
                 "experience_level":"SENIOR", "salary_min":50000,"salary_max":60000,
                 "skills":["%s"]}
                """.formatted(name))).andExpect(status().isCreated());
        long created = jdbc.queryForObject("SELECT id FROM job_offers WHERE company_id = ? AND title = 'New offer'", Long.class, company);
        mvc.perform(delete("/api/v1/jobs/" + created)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/jobs/" + created).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/jobs/" + created).header("Authorization", bearer(outsider))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/jobs/" + created).header("Authorization", bearer(recruiter)))
            .andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(delete("/api/v1/jobs/" + created).header("Authorization", bearer(recruiter))).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM job_offers WHERE id = ?", Boolean.class, created)).isTrue();
    }

}
