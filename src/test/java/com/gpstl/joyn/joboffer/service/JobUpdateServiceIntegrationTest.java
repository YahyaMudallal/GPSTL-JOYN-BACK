package com.gpstl.joyn.joboffer.service;

import com.gpstl.joyn.joboffer.JobDatabaseTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Vérifie les écritures JPA et leur atomicité sur un vrai PostgreSQL, dans un schéma isolé. */
class JobUpdateServiceIntegrationTest extends JobDatabaseTestSupport {
    private static final String SCHEMA = "job_test_" + UUID.randomUUID().toString().replace("-", "");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) { configureDatabase(registry, SCHEMA); }
    @Autowired JobUpdateService service;


    @Test void patchUpdatesOnlySuppliedFieldsAndWritesAudit() {
        var result = service.patch(recruiter, job, Map.of("title", "Updated", "salary_max", 65000));
        assertThat(result).containsEntry("title", "Updated").containsEntry("description", "Description")
            .containsEntry("salary_min", new BigDecimal("50000.00"));
        assertThat(result.get("updated_at")).isInstanceOf(String.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job_offer_audit WHERE user_id = ? AND job_offer_id = ? AND action = 'UPDATE'", Integer.class, recruiter, job)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT updated_at > created_at - INTERVAL '1 second' FROM job_offers WHERE id = ?", Boolean.class, job)).isTrue();
    }

    @Test void salaryValidationIncludesExistingUnmodifiedBound() {
        assertError(400, () -> service.patch(recruiter, job, Map.of("salary_min", 70000)));
        assertError(400, () -> service.patch(recruiter, job, Map.of("salary_max", 40000)));
        assertThat(jdbc.queryForObject("SELECT title FROM job_offers WHERE id = ?", String.class, job)).isEqualTo("Original");
    }

    @Test void canClearNullableValuesAndRejectUnknownContract() {
        Map<String, Object> patch = new HashMap<>(); patch.put("salary_max", null); patch.put("location", null);
        assertThat(service.patch(recruiter, job, patch)).containsEntry("salary_max", null).containsEntry("location", null);
        assertError(400, () -> service.patch(admin, job, Map.of("contract_type_id", 32767)));
    }

    @Test void synchronizesSkillsWithoutDeletingSharedCatalogOrRequiredLevels() {
        String retained = jdbc.queryForObject("SELECT name FROM skills WHERE id = ?", String.class, skill);
        String newName = "New-" + UUID.randomUUID();
        var result = service.patch(recruiter, job, Map.of("skills", List.of(retained, newName, newName)));
        assertThat((List<?>) result.get("skills")).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT required_level FROM job_offer_skills WHERE job_offer_id = ? AND skill_id = ?", String.class, job, skill)).isEqualTo("ADVANCED");
        assertThat(jdbc.queryForObject("SELECT updated_at > created_at - INTERVAL '1 second' FROM job_offers WHERE id = ?", Boolean.class, job)).isTrue();
        service.patch(recruiter, job, Map.of("skills", List.of()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job_offer_skills WHERE job_offer_id = ?", Integer.class, job)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM skills WHERE id = ?", Integer.class, skill)).isEqualTo(1);
    }

    @Test void mapsAllEditableFieldsToJpaEntityAndSupportsClearingOptionalFields() {
        int apprenticeship = jdbc.queryForObject("SELECT id FROM contract_types WHERE code = 'APPRENTICESHIP'", Integer.class);
        Map<String, Object> body = new HashMap<>();
        body.put("title", "Alternance Java"); body.put("description", "Updated description");
        body.put("location", "Lyon"); body.put("contract_type_id", apprenticeship);
        body.put("salary_min", 1500); body.put("salary_max", 1800);
        body.put("salary_currency", "USD"); body.put("salary_period", "MONTH");
        body.put("remote_mode", "HYBRID"); body.put("status", "DRAFT");
        body.put("rhythm_type", "3/1"); body.put("start_date", "2027-09-01");
        body.put("duration_months", 12); body.put("degree_level_required", "BAC_5");
        body.put("experience_level_required", "MID");
        var result = service.patch(recruiter, job, body);
        assertThat(result).containsEntry("title", "Alternance Java").containsEntry("location", "Lyon")
            .containsEntry("salary_currency", "USD").containsEntry("salary_period", "MONTH")
            .containsEntry("remote_mode", "HYBRID").containsEntry("status", "DRAFT")
            .containsEntry("rhythm_type", "3/1").containsEntry("start_date", "2027-09-01")
            .containsEntry("degree_level_required", "BAC_5").containsEntry("experience_level_required", "MID");
        assertThat(((Number) result.get("contract_type_id")).intValue()).isEqualTo(apprenticeship);
        assertThat(((Number) result.get("duration_months")).intValue()).isEqualTo(12);
        assertThat(jdbc.queryForObject("SELECT contract_type_id FROM job_offers WHERE id = ?", Integer.class, job)).isEqualTo(apprenticeship);
        Map<String, Object> cleared = new HashMap<>();
        for (String field : List.of("location", "salary_min", "salary_max", "rhythm_type", "start_date",
                "duration_months", "degree_level_required", "experience_level_required")) cleared.put(field, null);
        assertThat(service.patch(recruiter, job, cleared)).containsAllEntriesOf(cleared);
    }

    @Test void emptyPatchUpdatesTimestampAndResponseReflectsDatabaseTrigger() {
        var result = service.patch(recruiter, job, Map.of());
        var stored = jdbc.queryForObject("SELECT updated_at FROM job_offers WHERE id = ?",
            (rs, row) -> rs.getObject(1, java.time.OffsetDateTime.class).toInstant().toString(), job);
        assertThat(result).containsEntry("title", "Original").containsEntry("updated_at", stored);
        assertThat(jdbc.queryForObject("SELECT updated_at > created_at - INTERVAL '1 second' FROM job_offers WHERE id = ?", Boolean.class, job)).isTrue();
    }

    @Test void ordinaryClosedStatusIsDistinctFromSoftDeletion() {
        var result = service.patch(recruiter, job, Map.of("status", "CLOSED"));
        assertThat(result).containsEntry("deleted_at", null);
        assertThat(service.patch(recruiter, job, Map.of("status", "PUBLISHED"))).containsEntry("status", "PUBLISHED");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollsBackJobAndSkillsIfAuditFails() {
        // Provoquer l’échec de l’audit pour vérifier le rollback de l’offre et des compétences.
        jdbc.execute("ALTER TABLE job_offer_audit ADD CONSTRAINT test_audit_failure CHECK (action <> 'UPDATE')");
        String newSkill = "Rollback-" + UUID.randomUUID();
        try {
            assertThatThrownBy(() -> service.patch(recruiter, job, Map.of("title", "Must rollback", "skills", List.of(newSkill))))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("SELECT title FROM job_offers WHERE id = ?", String.class, job)).isEqualTo("Original");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job_offer_skills WHERE job_offer_id = ? AND skill_id = ?", Integer.class, job, skill)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM skills WHERE name = ?", Integer.class, newSkill)).isZero();
        } finally {
            jdbc.execute("ALTER TABLE job_offer_audit DROP CONSTRAINT test_audit_failure");
        }
    }
    @Test void checksRolesAndCompanyAndRejectsDeletedOffers() {
        assertError(403, () -> service.patch(outsider, job, Map.of()));
        assertError(403, () -> service.patch(candidate, job, Map.of()));
        assertError(401, () -> service.patch(Long.MAX_VALUE, job, Map.of()));
        assertError(404, () -> service.patch(admin, Long.MAX_VALUE, Map.of()));
        assertThat(service.patch(admin, job, Map.of("title", "Admin updated"))).containsEntry("title", "Admin updated");
        jdbc.update("UPDATE job_offers SET status = 'CLOSED', deleted_at = NOW() WHERE id = ?", job);
        assertError(404, () -> service.patch(recruiter, job, Map.of("status", "PUBLISHED")));
    }


    @Test void existingCreationAndSecurityWorkWithThisEndpoint() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
        mvc.perform(get("/openapi/job-update.yaml")).andExpect(status().isOk());
        String name = "Shared-" + UUID.randomUUID();
        mvc.perform(post("/api/v1/recruiters/jobs").header("Authorization", bearer(recruiter))
            .contentType("application/json").content("""
                {"title":"New offer", "description":"Created through main's endpoint",
                 "contract_type":"CDI", "location":"Paris", "remote_policy":"HYBRID",
                 "experience_level":"SENIOR", "salary_min":50000,"salary_max":60000,
                 "skills":["%s"]}
                """.formatted(name))).andExpect(status().isCreated());
        long created = jdbc.queryForObject("SELECT id FROM job_offers WHERE company_id = ? AND title = 'New offer'", Long.class, company);
        mvc.perform(patch("/api/v1/jobs/" + created).contentType("application/json").content("{}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/jobs/" + created).header("Authorization", "Bearer invalid").contentType("application/json").content("{}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/jobs/" + created).header("Authorization", bearer(outsider)).contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/jobs/" + created).header("Authorization", bearer(recruiter))
            .contentType("application/json").content("{\"title\":\"Updated created offer\",\"skills\":[\"" + name.toUpperCase(Locale.ROOT) + "\"]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Updated created offer"))
            .andExpect(jsonPath("$.skills[0]").value(name));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM skills WHERE LOWER(name) = LOWER(?)", Integer.class, name)).isEqualTo(1);
    }

}
