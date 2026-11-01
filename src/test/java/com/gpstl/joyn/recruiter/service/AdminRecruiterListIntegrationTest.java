package com.gpstl.joyn.recruiter.service;

import com.gpstl.joyn.common.dto.PageResponse;
import com.gpstl.joyn.joboffer.JobDatabaseTestSupport;
import com.gpstl.joyn.recruiter.dto.AdminRecruiterItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminRecruiterListIntegrationTest extends JobDatabaseTestSupport {

    private static final String SCHEMA = "job_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        configureDatabase(registry, SCHEMA);
    }

    @Autowired
    private AdminRecruiterListService adminRecruiterListService;

    private Long createCompany(String name) {
        return insert("INSERT INTO companies(name) VALUES (?) RETURNING id", name);
    }

    private Long createUser(String email, String status) {
        return insert("INSERT INTO users(email, password_hash, role, status) VALUES (?, 'hash', 'RECRUITER', ?) RETURNING id", email, status);
    }

    private void createRecruiter(Long userId, Long companyId, String firstName, String lastName) {
        insert("INSERT INTO recruiter_profiles(user_id, company_id, first_name, last_name) VALUES (?, ?, ?, ?) RETURNING id",
                userId, companyId, firstName, lastName);
    }

    @Test
    @DisplayName("listRecruiters() - Filtre par recherche textuelle (insensible à la casse sur l'entreprise)")
    void listRecruiters_SearchByCompanyName() {
        Long compId1 = createCompany("AlphaTech");
        Long compId2 = createCompany("BetaCorp");

        Long u1 = createUser("alice." + UUID.randomUUID() + "@test.com", "ACTIVE");
        Long u2 = createUser("bob." + UUID.randomUUID() + "@test.com", "ACTIVE");

        createRecruiter(u1, compId1, "Alice", "Durand");
        createRecruiter(u2, compId2, "Bob", "Lemoine");

        PageResponse<AdminRecruiterItem> result = adminRecruiterListService.listRecruiters(
                1, 10, "alphatech", null, null, "created_at", "desc"
        );

        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).companyName()).isEqualTo("AlphaTech");
        assertThat(result.data().get(0).firstName()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("listRecruiters() - Filtre par statut utilisateur (SUSPENDED)")
    void listRecruiters_FilterByStatus() {
        Long compId = createCompany("DeltaCorp-" + UUID.randomUUID());
        Long u1 = createUser("active." + UUID.randomUUID() + "@test.com", "ACTIVE");
        Long u2 = createUser("suspended." + UUID.randomUUID() + "@test.com", "SUSPENDED");

        createRecruiter(u1, compId, "ActiveUser", "One");
        createRecruiter(u2, compId, "SuspendedUser", "Two");

        PageResponse<AdminRecruiterItem> result = adminRecruiterListService.listRecruiters(
                1, 10, null, "SUSPENDED", null, "created_at", "desc"
        );

        assertThat(result.data())
                .isNotEmpty()
                .allMatch(item -> "SUSPENDED".equals(item.userStatus()));
    }
}