package com.gpstl.joyn.application.controller;

import com.gpstl.joyn.config.SecurityConfig;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ApplicationControllerIntegrationTest {

    private static final String URL = "/api/v1/applications";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${joyn.security.jwt.secret}")
    private String jwtSecret;

    private MockMvc mockMvc;
    private final String run = UUID.randomUUID().toString().substring(0, 8);

    private Long candidateUserId;
    private Long candidateProfileId;
    private Long candidateWithoutProfileId;
    private Long recruiterUserId;
    private Long otherRecruiterUserId;
    private Long adminUserId;
    private Long companyId;
    private Long otherCompanyId;
    private Long recruiterProfileId;
    private Long otherRecruiterProfileId;
    private Long publishedJobId;
    private Long draftJobId;
    private Long otherCompanyJobId;
    private Long expiredJobId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        companyId = insertReturningId("INSERT INTO companies (name) VALUES (?) RETURNING id", "Company " + run);
        otherCompanyId = insertReturningId(
                "INSERT INTO companies (name) VALUES (?) RETURNING id", "Other Company " + run);
        candidateUserId = insertUser("candidate-" + run + "@example.com", "CANDIDATE");
        candidateWithoutProfileId = insertUser("orphan-candidate-" + run + "@example.com", "CANDIDATE");
        recruiterUserId = insertUser("recruiter-" + run + "@example.com", "RECRUITER");
        otherRecruiterUserId = insertUser("other-recruiter-" + run + "@example.com", "RECRUITER");
        adminUserId = insertUser("admin-" + run + "@example.com", "ADMIN");

        candidateProfileId = insertReturningId("""
                INSERT INTO candidate_profiles (user_id, first_name, last_name)
                VALUES (?, 'Casey', 'Candidate') RETURNING id
                """, candidateUserId);
        recruiterProfileId = insertRecruiter(recruiterUserId, companyId);
        otherRecruiterProfileId = insertRecruiter(otherRecruiterUserId, otherCompanyId);

        Short contractTypeId = jdbc.queryForObject(
                "SELECT id FROM contract_types WHERE code = 'APPRENTICESHIP'", Short.class);
        publishedJobId = insertJob(companyId, recruiterProfileId, contractTypeId, "PUBLISHED");
        draftJobId = insertJob(companyId, recruiterProfileId, contractTypeId, "DRAFT");
        otherCompanyJobId = insertJob(otherCompanyId, otherRecruiterProfileId, contractTypeId, "PUBLISHED");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM applications WHERE candidate_id = ? OR job_offer_id IN (?, ?, ?, ?)",
                candidateProfileId, publishedJobId, draftJobId, otherCompanyJobId, expiredJobId);
        jdbc.update("DELETE FROM job_offers WHERE id IN (?, ?, ?, ?)",
                publishedJobId, draftJobId, otherCompanyJobId, expiredJobId);
        jdbc.update("DELETE FROM recruiter_profiles WHERE id IN (?, ?)", recruiterProfileId, otherRecruiterProfileId);
        jdbc.update("DELETE FROM candidate_profiles WHERE id = ?", candidateProfileId);
        jdbc.update("DELETE FROM companies WHERE id IN (?, ?)", companyId, otherCompanyId);
        jdbc.update("DELETE FROM users WHERE id IN (?, ?, ?, ?, ?)",
                candidateUserId, candidateWithoutProfileId, recruiterUserId, otherRecruiterUserId, adminUserId);
    }

    @Test
    void createsApplicationForJwtCandidateAndStoresSchemaFields() throws Exception {
        mockMvc.perform(post(URL)
                        .header("Authorization", bearer(candidateUserId, "CANDIDATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(publishedJobId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.candidateId").value(candidateProfileId))
                .andExpect(jsonPath("$.jobOfferId").value(publishedJobId))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.coverMessage").value("Please consider my application."))
                .andExpect(jsonPath("$.resumeUrl").value("https://example.com/resume.pdf"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM applications WHERE candidate_id = ? AND job_offer_id = ?",
                Integer.class, candidateProfileId, publishedJobId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void requiresCandidateAuthenticationAndCandidateProfile() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(publishedJobId)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(URL)
                        .header("Authorization", bearer(recruiterUserId, "RECRUITER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(publishedJobId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(URL)
                        .header("Authorization", bearer(candidateWithoutProfileId, "CANDIDATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(publishedJobId)))
                .andExpect(status().isForbidden());
        assertNoApplications();
    }

    @Test
    void rejectsSuspendedCandidateAccount() throws Exception {
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", candidateUserId);

        mockMvc.perform(post(URL)
                        .header("Authorization", bearer(candidateUserId, "CANDIDATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(publishedJobId)))
                .andExpect(status().isForbidden());
        assertNoApplications();
    }

    @Test
    void rejectsMalformedJwtIdentityAndInvalidRequest() throws Exception {
        mockMvc.perform(post(URL)
                        .header("Authorization", bearer("not-a-user-id", "CANDIDATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(publishedJobId)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid authenticated user identity"));

        mockMvc.perform(post(URL)
                        .header("Authorization", bearer(candidateUserId, "CANDIDATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobOfferId\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.errors[0].field").value("jobOfferId"));

        mockMvc.perform(post(URL)
                        .header("Authorization", bearer(candidateUserId, "CANDIDATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{malformed"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body or path parameter is invalid"))
                .andExpect(jsonPath("$.errors").isEmpty());

        mockMvc.perform(patch(URL + "/not-an-id/status")
                        .header("Authorization", bearer(adminUserId, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        assertNoApplications();
    }

    @Test
    void requiresAnExistingPublishedJobAndPreventsDuplicateApplications() throws Exception {
        mockMvc.perform(post(URL)
                        .header("Authorization", bearer(candidateUserId, "CANDIDATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(Long.MAX_VALUE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Job offer not found"));

        mockMvc.perform(post(URL)
                        .header("Authorization", bearer(candidateUserId, "CANDIDATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(draftJobId)))
                .andExpect(status().isNotFound());

        expiredJobId = insertExpiredJob();
        mockMvc.perform(post(URL)
                        .header("Authorization", bearer(candidateUserId, "CANDIDATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(expiredJobId)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(URL)
                        .header("Authorization", bearer(candidateUserId, "CANDIDATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(publishedJobId)))
                .andExpect(status().isCreated());
        mockMvc.perform(post(URL)
                        .header("Authorization", bearer(candidateUserId, "CANDIDATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(publishedJobId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Candidate has already applied to this job offer"));
    }

    @Test
    void allowsJobOwningRecruiterToUpdateStatusAndMarksViewed() throws Exception {
        Long applicationId = insertApplication();

        mockMvc.perform(patch(URL + "/" + applicationId + "/status")
                        .header("Authorization", bearer(recruiterUserId, "RECRUITER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"VIEWED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VIEWED"))
                .andExpect(jsonPath("$.viewedAt").isNotEmpty())
                .andExpect(jsonPath("$.candidateId").value(candidateProfileId))
                .andExpect(jsonPath("$.jobOfferId").value(publishedJobId));

        Integer viewed = jdbc.queryForObject(
                "SELECT count(*) FROM applications a JOIN application_statuses s ON s.id = a.status_id "
                        + "WHERE a.id = ? AND s.code = 'VIEWED' AND a.viewed_at IS NOT NULL",
                Integer.class, applicationId);
        assertThat(viewed).isEqualTo(1);
    }

    @Test
    void protectsStatusUpdateFromUnauthenticatedWrongRoleAndDifferentCompany() throws Exception {
        Long applicationId = insertApplication();

        mockMvc.perform(patch(URL + "/" + applicationId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch(URL + "/" + applicationId + "/status")
                        .header("Authorization", bearer(candidateUserId, "CANDIDATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch(URL + "/" + applicationId + "/status")
                        .header("Authorization", bearer(otherRecruiterUserId, "RECRUITER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isForbidden());
        assertStatus(applicationId, "PENDING");
    }

    @Test
    void allowsAdministratorAndRejectsMissingApplicationOrInvalidStatus() throws Exception {
        Long applicationId = insertApplication();
        mockMvc.perform(patch(URL + "/" + applicationId + "/status")
                        .header("Authorization", bearer(adminUserId, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        mockMvc.perform(patch(URL + "/" + Long.MAX_VALUE + "/status")
                        .header("Authorization", bearer(adminUserId, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch(URL + "/" + applicationId + "/status")
                        .header("Authorization", bearer(adminUserId, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INVALID\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").value("status"));
    }

    private Long insertUser(String email, String role) {
        return insertReturningId(
                "INSERT INTO users (email, password_hash, role) VALUES (?, 'test-hash', ?) RETURNING id",
                email, role);
    }

    private Long insertRecruiter(Long userId, Long company) {
        return insertReturningId("""
                INSERT INTO recruiter_profiles (user_id, company_id, first_name, last_name)
                VALUES (?, ?, 'Riley', 'Recruiter') RETURNING id
                """, userId, company);
    }

    private Long insertJob(Long company, Long recruiter, Short contractType, String status) {
        return insertReturningId("""
                INSERT INTO job_offers (
                    company_id, recruiter_id, contract_type_id, title, description, status, published_at
                ) VALUES (?, ?, ?, 'Application test role', 'Test description', ?, NOW())
                RETURNING id
                """, company, recruiter, contractType, status);
    }

    private Long insertApplication() {
        Short pendingId = jdbc.queryForObject(
                "SELECT id FROM application_statuses WHERE code = 'PENDING'", Short.class);
        return insertReturningId("""
                INSERT INTO applications (candidate_id, job_offer_id, status_id)
                VALUES (?, ?, ?) RETURNING id
                """, candidateProfileId, publishedJobId, pendingId);
    }

    private Long insertExpiredJob() {
        Short contractTypeId = jdbc.queryForObject(
                "SELECT id FROM contract_types WHERE code = 'APPRENTICESHIP'", Short.class);
        return insertReturningId("""
                INSERT INTO job_offers (
                    company_id, recruiter_id, contract_type_id, title, description,
                    status, published_at, expires_at
                ) VALUES (?, ?, ?, 'Expired application test role', 'Test description',
                    'PUBLISHED', NOW(), NOW() - INTERVAL '1 day')
                RETURNING id
                """, companyId, recruiterProfileId, contractTypeId);
    }

    private Long insertReturningId(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private String createBody(Long jobId) {
        return """
                {
                  "jobOfferId": %d,
                  "coverMessage": "Please consider my application.",
                  "resumeUrl": "https://example.com/resume.pdf"
                }
                """.formatted(jobId);
    }

    private void assertNoApplications() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM applications WHERE candidate_id = ?", Integer.class, candidateProfileId);
        assertThat(count).isZero();
    }

    private void assertStatus(Long applicationId, String expectedCode) {
        String status = jdbc.queryForObject(
                "SELECT s.code FROM applications a JOIN application_statuses s ON s.id = a.status_id WHERE a.id = ?",
                String.class, applicationId);
        assertThat(status).isEqualTo(expectedCode);
    }

    private String bearer(Long userId, String role) {
        return bearer(String.valueOf(userId), role);
    }

    private String bearer(String subject, String role) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(SecurityConfig.secretKey(jwtSecret)));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject)
                .claim("role", role)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
