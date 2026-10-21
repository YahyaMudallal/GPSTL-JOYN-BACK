package com.gpstl.joyn.recruiter.service;

import com.gpstl.joyn.joboffer.JobDatabaseTestSupport;
import com.gpstl.joyn.recruiter.dto.RecruiterRegisterRequest;
import com.gpstl.joyn.recruiter.dto.RecruiterRegisterResponse;
import com.gpstl.joyn.recruiter.services.RecruiterService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecruiterServiceIntegrationTest extends JobDatabaseTestSupport {

    private static final String SCHEMA = "job_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        configureDatabase(registry, SCHEMA);
    }

    @Autowired
    private RecruiterService recruiterService;

    @Test
    @DisplayName("register() - Crée correctement l'User, l'Entreprise et le Profil en BDD")
    void shouldRegisterNewRecruiterWithNewCompany() {
        String uniqueEmail = "recruiter." + UUID.randomUUID() + "@joyn.test";
        String companyName = "Startup-" + UUID.randomUUID();

        RecruiterRegisterRequest request = new RecruiterRegisterRequest(
                uniqueEmail,
                "secret-pass",
                "Alice",
                "Durand",
                companyName
        );

        RecruiterRegisterResponse response = recruiterService.register(request);

        assertThat(response.profileId()).isNotNull();
        assertThat(response.userId()).isNotNull();
        assertThat(response.email()).isEqualTo(uniqueEmail);
        assertThat(response.companyName()).isEqualTo(companyName);

        // Vérification directe dans PostgreSQL via JdbcTemplate
        Integer userCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ? AND role = 'RECRUITER'",
                Integer.class,
                uniqueEmail
        );
        assertThat(userCount).isEqualTo(1);

        Integer companyCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM companies WHERE name = ?",
                Integer.class,
                companyName
        );
        assertThat(companyCount).isEqualTo(1);

        Integer profileCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM recruiter_profiles WHERE user_id = ?",
                Integer.class,
                response.userId()
        );
        assertThat(profileCount).isEqualTo(1);
    }

    @Test
    @DisplayName("register() - Réutilise l'entreprise existante sans en créer une deuxième")
    void shouldReuseExistingCompanyWhenRegistering() {
        String existingCompanyName = "ExistingCorp-" + UUID.randomUUID();
        // Pré-création d'une entreprise existante
        insert("INSERT INTO companies(name) VALUES (?) RETURNING id", existingCompanyName);

        String email = "recruiter2." + UUID.randomUUID() + "@joyn.test";
        RecruiterRegisterRequest request = new RecruiterRegisterRequest(
                email,
                "secret-pass",
                "Bob",
                "Lemoine",
                existingCompanyName
        );

        RecruiterRegisterResponse response = recruiterService.register(request);

        assertThat(response.companyName()).isEqualTo(existingCompanyName);

        // On vérifie qu'il n'y a toujours qu'une seule entreprise de ce nom
        Integer companyCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM companies WHERE name = ?",
                Integer.class,
                existingCompanyName
        );
        assertThat(companyCount).isEqualTo(1);
    }

    @Test
    @DisplayName("register() - Lève IllegalArgumentException en cas d'email déjà utilisé")
    void shouldThrowExceptionWhenEmailAlreadyExists() {
        String email = "shared." + UUID.randomUUID() + "@joyn.test";
        // Pré-création de l'utilisateur
        insert("INSERT INTO users(email, password_hash, role) VALUES (?, 'hash', 'CANDIDATE') RETURNING id", email);

        RecruiterRegisterRequest request = new RecruiterRegisterRequest(
                email,
                "password",
                "Paul",
                "Bernard",
                "SomeCorp"
        );

        assertThatThrownBy(() -> recruiterService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cet email est déjà associé à un compte.");
    }
}