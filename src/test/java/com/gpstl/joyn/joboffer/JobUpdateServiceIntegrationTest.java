package com.gpstl.joyn.joboffer;

import com.gpstl.joyn.joboffer.service.JobUpdateService;
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
        service.patch(recruiter, job, Map.of("skills", List.of()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job_offer_skills WHERE job_offer_id = ?", Integer.class, job)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM skills WHERE id = ?", Integer.class, skill)).isEqualTo(1);
    }

    @Test void ordinaryClosedStatusIsDistinctFromSoftDeletion() {
        var result = service.patch(recruiter, job, Map.of("status", "CLOSED"));
        assertThat(result).containsEntry("deleted_at", null);
        assertThat(service.patch(recruiter, job, Map.of("status", "PUBLISHED"))).containsEntry("status", "PUBLISHED");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollsBackJobAndSkillsIfAuditFails() {
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
