package com.gpstl.joyn.joboffer.service;

import com.gpstl.joyn.joboffer.JobDatabaseTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.UUID;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminCompanyPatchAccessTest extends JobDatabaseTestSupport {
    private static final String SCHEMA = "job_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) { configureDatabase(registry, SCHEMA); }

    @org.springframework.beans.factory.annotation.Value("${joyn.security.jwt.secret}")
    String signingSecret;
    private String token(long id, String role) {
        var encoder = new org.springframework.security.oauth2.jwt.NimbusJwtEncoder(
            new com.nimbusds.jose.jwk.source.ImmutableSecret<>(com.gpstl.joyn.config.SecurityConfig.secretKey(signingSecret)));
        var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder().subject(Long.toString(id))
            .claim("role", role).issuedAt(java.time.Instant.now()).expiresAt(java.time.Instant.now().plusSeconds(300)).build();
        return "Bearer " + encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
            org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
    @Test
    void administratorMayEditCompanyButCandidateMayNot() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
        mvc.perform(patch("/api/v1/companies/" + company).header("Authorization", token(candidate, "CANDIDATE"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Unauthorized\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/companies/" + company).header("Authorization", token(admin, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Updated company\"}"))
                .andExpect(status().isOk());
    }
}
