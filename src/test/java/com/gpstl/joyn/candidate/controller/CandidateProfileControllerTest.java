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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Routes HTTP du profil avec le vrai SecurityConfig et de vrais tokens JWT signés.
 * Aucune route de connexion n'existe encore : les tokens sont fabriqués avec la clé de configuration.
 */
class CandidateProfileControllerTest extends JobDatabaseTestSupport {
    private static final String SCHEMA = "job_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private static final String ROUTE = "/api/v1/candidates/profile";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) { configureDatabase(registry, SCHEMA); }

    @Value("${joyn.security.jwt.secret}") String secret;
    private MockMvc mvc;
    private long profile;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
        profile = insert("INSERT INTO candidate_profiles(user_id, first_name, last_name) VALUES (?, 'Old', 'Name') RETURNING id", candidate);
    }

    private String byIdRoute() {
        return "/api/v1/candidates/" + profile + "/profile";
    }

    private String token(long userId, String role, String key) {
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(SecurityConfig.secretKey(key)));
        var claims = JwtClaimsSet.builder().subject(Long.toString(userId)).claim("role", role)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private String token(long userId, String role) {
        return token(userId, role, secret);
    }

    @Test
    void candidateReadsOwnProfile() throws Exception {
        mvc.perform(get(ROUTE).header("Authorization", token(candidate, "CANDIDATE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.first_name").value("Old"))
                .andExpect(jsonPath("$.preferences.currency").value("EUR"))
                .andExpect(jsonPath("$.skills").isArray());
    }

    @Test
    void candidatePatchesOwnProfileWithSnakeCaseJson() throws Exception {
        mvc.perform(patch(ROUTE).header("Authorization", token(candidate, "CANDIDATE"))
                        .contentType("application/json")
                        .content("{\"headline\":\"Dev\",\"preferences\":{\"contract_types\":[\"CDI\"],\"min_salary\":48000}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline").value("Dev"))
                .andExpect(jsonPath("$.last_name").value("Name"))
                .andExpect(jsonPath("$.preferences.contract_types[0]").value("CDI"))
                .andExpect(jsonPath("$.preferences.min_salary").value(48000))
                .andExpect(jsonPath("$.updated_at").exists());
    }

    @Test
    void missingOrForgedTokenGets401() throws Exception {
        mvc.perform(get(ROUTE)).andExpect(status().isUnauthorized());
        mvc.perform(get(ROUTE).header("Authorization",
                token(candidate, "CANDIDATE", "une-autre-cle-secrete-de-plus-de-32-caracteres!")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recruiterTokenGets403BeforeTheBodyIsRead() throws Exception {
        mvc.perform(patch(ROUTE).header("Authorization", token(recruiter, "RECRUITER"))
                        .contentType("application/json").content("{\"bio\":5}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void roleIsCheckedAgainstTheDatabaseToo() throws Exception {
        // Token qui annonce CANDIDATE pour un compte RECRUITER en base : refusé par le service.
        mvc.perform(get(ROUTE).header("Authorization", token(recruiter, "CANDIDATE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only candidates have a candidate profile"));
    }

    @Test
    void invalidFieldsGetDetailed400() throws Exception {
        mvc.perform(patch(ROUTE).header("Authorization", token(candidate, "CANDIDATE"))
                        .contentType("application/json").content("{\"email\":\"x@y.fr\",\"phone\":\"06\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid profile patch"))
                .andExpect(jsonPath("$.errors.email").value("Protected field: it cannot be modified by this route"))
                .andExpect(jsonPath("$.errors.phone").exists());
    }

    @Test
    void malformedOrMissingBodyGets400() throws Exception {
        mvc.perform(patch(ROUTE).header("Authorization", token(candidate, "CANDIDATE"))
                        .contentType("application/json").content("{bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body must be a valid JSON object"));
        mvc.perform(patch(ROUTE).header("Authorization", token(candidate, "CANDIDATE"))
                        .contentType("application/json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.body").exists());
    }

    @Test
    void wrongContentTypeGets415() throws Exception {
        mvc.perform(patch(ROUTE).header("Authorization", token(candidate, "CANDIDATE"))
                        .contentType("text/plain").content("x"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void recruiterAndAdministratorReadAGivenProfile() throws Exception {
        jdbc.update("UPDATE candidate_profiles SET show_last_name = FALSE WHERE id = ?", profile);
        // Recruteur : vue filtrée par les réglages du candidat, sans les réglages eux-mêmes.
        mvc.perform(get(byIdRoute()).header("Authorization", token(recruiter, "RECRUITER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(profile))
                .andExpect(jsonPath("$.last_name").value("N."))
                .andExpect(jsonPath("$.visibility").doesNotExist());
        // Administrateur : vue complète.
        mvc.perform(get(byIdRoute()).header("Authorization", token(admin, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.last_name").value("Name"))
                .andExpect(jsonPath("$.visibility.show_last_name").value(false));
        mvc.perform(get("/api/v1/candidates/999999999/profile").header("Authorization", token(recruiter, "RECRUITER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/candidates/abc/profile").header("Authorization", token(recruiter, "RECRUITER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheCandidateCanModifyTheProfile() throws Exception {
        // Aucune route PATCH par identifiant : un recruteur ou un admin ne peut pas modifier un profil.
        mvc.perform(patch(byIdRoute()).header("Authorization", token(admin, "ADMIN"))
                        .contentType("application/json").content("{\"bio\":\"x\"}"))
                .andExpect(status().isMethodNotAllowed());
        // Un admin n'a pas de profil candidat : la route /profile lui est fermée.
        mvc.perform(patch(ROUTE).header("Authorization", token(admin, "ADMIN"))
                        .contentType("application/json").content("{\"bio\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void readingByIdIsClosedToCandidatesAndAnonymousCallers() throws Exception {
        mvc.perform(get(byIdRoute()).header("Authorization", token(candidate, "CANDIDATE")))
                .andExpect(status().isForbidden());
        mvc.perform(get(byIdRoute())).andExpect(status().isUnauthorized());
        // Token qui annonce RECRUITER pour un compte CANDIDATE en base : refusé par le service.
        mvc.perform(get(byIdRoute()).header("Authorization", token(candidate, "RECRUITER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only recruiters and administrators can view another candidate's profile"));
    }

    @Test
    void openApiDocumentIsServedAndReferencedBySpringdoc() throws Exception {
        String yaml = mvc.perform(get("/openapi/candidate-profile.yaml")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var api = io.swagger.v3.core.util.Yaml.mapper().readValue(yaml, io.swagger.v3.oas.models.OpenAPI.class);
        org.assertj.core.api.Assertions.assertThat(api.getPaths())
                .containsKeys(ROUTE, "/api/v1/candidates/{candidateId}/profile");
        // Chaque référence interne du document doit exister.
        var refs = java.util.regex.Pattern.compile("#/components/(schemas|responses)/([A-Za-z]+)").matcher(yaml);
        while (refs.find()) {
            var section = refs.group(1).equals("schemas") ? api.getComponents().getSchemas() : api.getComponents().getResponses();
            org.assertj.core.api.Assertions.assertThat(section).as(refs.group()).containsKey(refs.group(2));
        }
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/candidates/profile'].patch.operationId").value("updateCandidateProfile"))
                .andExpect(jsonPath("$.paths['/api/v1/candidates/{candidateId}/profile'].get.operationId").value("getCandidateProfileById"))
                .andExpect(jsonPath("$.paths['/api/v1/candidates/{candidateId}/profile'].patch").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/candidates/profile'].patch.requestBody.content['application/json'].schema['$ref']")
                        .value("/openapi/candidate-profile.yaml#/components/schemas/CandidateProfilePatch"));
    }

    @Test
    void candidateWithoutProfileGets404() throws Exception {
        jdbc.update("DELETE FROM candidate_profiles WHERE user_id = ?", candidate);
        mvc.perform(get(ROUTE).header("Authorization", token(candidate, "CANDIDATE")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Candidate profile not found"));
    }
}
