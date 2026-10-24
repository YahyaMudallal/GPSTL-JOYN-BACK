package com.gpstl.joyn.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Parcours complet : un recruteur s'inscrit (R55) puis se connecte (U16).
 * Vérifie aussi que le mot de passe est bien stocké haché, et plus en clair.
 */
@SpringBootTest
class RegisterThenLoginTest {

    private static final String PASSWORD = "MotDePasse123!";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    private final String run = UUID.randomUUID().toString().substring(0, 8);
    private final String email = "inscription-" + run + "@joyn.test";
    private final String companyName = "Entreprise Login " + run;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM recruiter_profiles WHERE user_id IN (SELECT id FROM users WHERE email = ?)", email);
        jdbc.update("DELETE FROM users WHERE email = ?", email);
        jdbc.update("DELETE FROM companies WHERE name = ?", companyName);
    }

    @Test
    void registeredRecruiterIsStoredWithHashedPasswordAndCanLogIn() throws Exception {
        String registration = """
                {"email": "%s", "password": "%s", "firstName": "Camille", "lastName": "Martin", "companyName": "%s"}"""
                .formatted(email, PASSWORD, companyName);
        mockMvc.perform(post("/api/recruiters/register").contentType(MediaType.APPLICATION_JSON).content(registration))
                .andExpect(status().isCreated());

        // Le mot de passe n'est plus stocké en clair, mais sous forme de hash BCrypt.
        String storedHash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, email);
        assertThat(storedHash).isNotEqualTo(PASSWORD).startsWith("$2");
        assertThat(passwordEncoder.matches(PASSWORD, storedHash)).isTrue();

        // Et le recruteur peut se connecter avec son mot de passe.
        String login = """
                {"email": "%s", "password": "%s"}""".formatted(email, PASSWORD);
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("RECRUITER"));
    }
}
