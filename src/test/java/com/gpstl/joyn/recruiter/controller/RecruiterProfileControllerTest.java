package com.gpstl.joyn.recruiter.controller;

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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Route du profil recruteur avec le vrai SecurityConfig, de vrais tokens JWT signés et une vraie base. */
class RecruiterProfileControllerTest extends JobDatabaseTestSupport {
    private static final String SCHEMA = "job_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private static final String ROUTE = "/api/v1/recruiters/profile";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) { configureDatabase(registry, SCHEMA); }

    @Value("${joyn.security.jwt.secret}") String secret;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
    }

    private String token(long userId, String role) {
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(SecurityConfig.secretKey(secret)));
        var claims = JwtClaimsSet.builder().subject(Long.toString(userId)).claim("role", role)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private ResultActions send(long userId, String role, String json) throws Exception {
        return mvc.perform(patch(ROUTE).header("Authorization", token(userId, role))
                .contentType("application/json").content(json));
    }

    private ResultActions send(String json) throws Exception {
        return send(recruiter, "RECRUITER", json);
    }

    private String names(long userId) {
        return jdbc.queryForObject("SELECT first_name || ' ' || last_name FROM recruiter_profiles WHERE user_id = ?",
                String.class, userId);
    }

    private ResultActions read(long userId, String role) throws Exception {
        return mvc.perform(get(ROUTE).header("Authorization", token(userId, role)));
    }

    @Test
    void recruiterReadsOwnProfile() throws Exception {
        String email = jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, recruiter);
        read(recruiter, "RECRUITER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId").value(profile))
                .andExpect(jsonPath("$.userId").value(recruiter))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.firstName").value("A"))
                .andExpect(jsonPath("$.lastName").value("B"))
                .andExpect(jsonPath("$.companyName").value("Owner"));
    }

    @Test
    void readReturnsTheUpdatedName() throws Exception {
        send("{\"firstName\":\"Marie\"}").andExpect(status().isOk());
        read(recruiter, "RECRUITER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Marie"))
                .andExpect(jsonPath("$.lastName").value("B"));
    }

    @Test
    void readIsRefusedWithoutAValidRecruiterAccount() throws Exception {
        mvc.perform(get(ROUTE)).andExpect(status().isUnauthorized());
        read(Long.MAX_VALUE, "RECRUITER").andExpect(status().isUnauthorized());
        read(candidate, "CANDIDATE").andExpect(status().isForbidden());
        read(admin, "ADMIN").andExpect(status().isForbidden());
        read(candidate, "RECRUITER").andExpect(status().isForbidden());
    }

    @Test
    void suspendedRecruiterCannotRead() throws Exception {
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", recruiter);
        read(recruiter, "RECRUITER")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Account is suspended"));
    }

    @Test
    void readWithoutProfileGets404() throws Exception {
        long withoutProfile = insert("INSERT INTO users(email, password_hash, role) VALUES (?, 'test-only', 'RECRUITER') RETURNING id",
                UUID.randomUUID() + "@example.test");
        read(withoutProfile, "RECRUITER")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Recruiter profile not found"));
    }

    @Test
    void recruiterUpdatesOwnNameAndValuesAreTrimmed() throws Exception {
        send("{\"firstName\":\"  Marie \",\"lastName\":\" Dupont  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId").value(profile))
                .andExpect(jsonPath("$.userId").value(recruiter))
                .andExpect(jsonPath("$.email").exists())
                .andExpect(jsonPath("$.firstName").value("Marie"))
                .andExpect(jsonPath("$.lastName").value("Dupont"))
                .andExpect(jsonPath("$.companyName").value("Owner"));
        assertThat(names(recruiter)).isEqualTo("Marie Dupont");
    }

    @Test
    void absentFieldIsKept() throws Exception {
        send("{\"firstName\":\"Marie\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("B"));
        assertThat(names(recruiter)).isEqualTo("Marie B");
    }

    @Test
    void onlyTheProfileOfTheTokenIsChanged() throws Exception {
        send("{\"firstName\":\"Marie\"}").andExpect(status().isOk());
        assertThat(names(outsider)).isEqualTo("C D");
    }

    @Test
    void updatedAtIsRefreshed() throws Exception {
        jdbc.update("UPDATE recruiter_profiles SET updated_at = NOW() - INTERVAL '1 day' WHERE id = ?", profile);
        send("{\"lastName\":\"Dupont\"}").andExpect(status().isOk());
        Boolean refreshed = jdbc.queryForObject(
                "SELECT updated_at > NOW() - INTERVAL '1 hour' FROM recruiter_profiles WHERE id = ?", Boolean.class, profile);
        assertThat(refreshed).isTrue();
    }

    @Test
    void accountAndCompanyFieldsAreRejectedAndNothingChanges() throws Exception {
        String email = jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, recruiter);
        send("""
            {"firstName":"Marie","email":"new@example.test","password":"secret-password","role":"ADMIN",
             "status":"SUSPENDED","companyId":1,"company_id":1,"userId":1}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.role").exists())
                .andExpect(jsonPath("$.errors.status").exists())
                .andExpect(jsonPath("$.errors.companyId").exists())
                .andExpect(jsonPath("$.errors.company_id").exists())
                .andExpect(jsonPath("$.errors.userId").exists());
        assertThat(names(recruiter)).isEqualTo("A B");
        assertThat(jdbc.queryForMap("SELECT email, role, status FROM users WHERE id = ?", recruiter))
                .containsEntry("email", email).containsEntry("role", "RECRUITER").containsEntry("status", "ACTIVE");
        assertThat(jdbc.queryForObject("SELECT company_id FROM recruiter_profiles WHERE id = ?", Long.class, profile))
                .isEqualTo(company);
    }

    @Test
    void invalidNamesGetErrorsPerField() throws Exception {
        send("{\"firstName\":\"   \",\"lastName\":null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.firstName").value("Must not be blank"))
                .andExpect(jsonPath("$.errors.lastName").value("Expected a non-empty string"));
        send("{\"firstName\":5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.firstName").value("Expected a non-empty string"));
        send("{\"firstName\":\"" + "x".repeat(101) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.firstName").value("Must not exceed 100 characters"));
        send("{\"firstName\":\"Ma\\u0000rie\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.firstName").value("Must not contain control characters"));
        assertThat(names(recruiter)).isEqualTo("A B");
    }

    @Test
    void nameOfMaximumLengthIsAccepted() throws Exception {
        send("{\"firstName\":\"" + "x".repeat(100) + "\"}").andExpect(status().isOk());
    }

    @Test
    void invalidBodiesGet400() throws Exception {
        send("{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.body").exists());
        send("{not json").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body must be a valid JSON object"));
        send("[]").andExpect(status().isBadRequest());
        mvc.perform(patch(ROUTE).header("Authorization", token(recruiter, "RECRUITER")).contentType("application/json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrongContentTypeGets415() throws Exception {
        mvc.perform(patch(ROUTE).header("Authorization", token(recruiter, "RECRUITER"))
                        .contentType("text/plain").content("Marie"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void missingTokenGets401() throws Exception {
        mvc.perform(patch(ROUTE).contentType("application/json").content("{\"firstName\":\"Marie\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenOfADeletedUserGets401() throws Exception {
        send(Long.MAX_VALUE, "RECRUITER", "{\"firstName\":\"Marie\"}").andExpect(status().isUnauthorized());
    }

    @Test
    void otherRolesGet403() throws Exception {
        send(candidate, "CANDIDATE", "{\"firstName\":\"Marie\"}").andExpect(status().isForbidden());
        send(admin, "ADMIN", "{\"firstName\":\"Marie\"}").andExpect(status().isForbidden());
    }

    @Test
    void roleIsCheckedAgainstTheDatabaseToo() throws Exception {
        // Token qui annonce RECRUITER pour un compte CANDIDATE en base : refusé par le service.
        send(candidate, "RECRUITER", "{\"firstName\":\"Marie\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only recruiters have a recruiter profile"));
    }

    @Test
    void suspendedRecruiterWithAStillValidTokenGets403() throws Exception {
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", recruiter);
        send("{\"firstName\":\"Marie\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Account is suspended"));
        assertThat(names(recruiter)).isEqualTo("A B");
    }

    @Test
    void accountIsCheckedBeforeTheBody() throws Exception {
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", recruiter);
        send("{\"email\":\"new@example.test\"}").andExpect(status().isForbidden());
    }

    @Test
    void recruiterWithoutProfileGets404() throws Exception {
        long withoutProfile = insert("INSERT INTO users(email, password_hash, role) VALUES (?, 'test-only', 'RECRUITER') RETURNING id",
                UUID.randomUUID() + "@example.test");
        send(withoutProfile, "RECRUITER", "{\"firstName\":\"Marie\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Recruiter profile not found"));
    }
}
