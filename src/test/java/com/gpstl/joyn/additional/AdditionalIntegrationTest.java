package com.gpstl.joyn.additional;

import com.gpstl.joyn.joboffer.JobDatabaseTestSupport;
import com.gpstl.joyn.config.SecurityConfig;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdditionalIntegrationTest extends JobDatabaseTestSupport {
    static final String SCHEMA="job_test_"+UUID.randomUUID().toString().replace("-","").substring(0,12);
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry){configureDatabase(registry,SCHEMA);}
    @Value("${joyn.security.jwt.secret}") String secret;
    @Autowired PasswordEncoder encoder;
    MockMvc mvc; long application;
    @BeforeEach void setup(){
        mvc=MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
        long cp=insert("INSERT INTO candidate_profiles(user_id,first_name,last_name,is_visible_to_recruiters) VALUES(?,'Ada','Lovelace',true) RETURNING id",candidate);
        application=insert("INSERT INTO applications(candidate_id,job_offer_id,status_id) VALUES(?,?,(SELECT id FROM application_statuses WHERE code='PENDING')) RETURNING id",cp,job);
    }
    String token(long id,String role){
        var jwt=new NimbusJwtEncoder(new ImmutableSecret<>(SecurityConfig.secretKey(secret)));
        var claims=JwtClaimsSet.builder().subject(""+id).claim("role",role).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        return "Bearer "+jwt.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
    }
    String proposal(OffsetDateTime time){return "{\"applicationId\":"+application+",\"scheduledAt\":\""+time+"\",\"durationMinutes\":30,\"mode\":\"VIDEO\",\"locationOrLink\":\"https://example.test/interview\"}";}
    @Test void realProposalConfirmationConflictAndVisibility() throws Exception {
        var time=OffsetDateTime.now(ZoneOffset.UTC).plusDays(2);
        String body=proposal(time);
        mvc.perform(post("/api/v1/interviews").header("Authorization",token(outsider,"RECRUITER")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        var result=mvc.perform(post("/api/v1/interviews").header("Authorization",token(recruiter,"RECRUITER")).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROPOSED")).andReturn();
        long id=new JsonMapper().readTree(result.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(post("/api/v1/interviews").header("Authorization",token(recruiter,"RECRUITER")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/interviews").header("Authorization",token(candidate,"CANDIDATE"))).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/interviews/"+id).header("Authorization",token(outsider,"RECRUITER"))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/interviews/"+id+"/status").header("Authorization",token(recruiter,"RECRUITER")).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMED\"}")).andExpect(status().isConflict());
        mvc.perform(patch("/api/v1/interviews/"+id+"/status").header("Authorization",token(candidate,"CANDIDATE")).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMED\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        mvc.perform(patch("/api/v1/interviews/"+id+"/status").header("Authorization",token(candidate,"CANDIDATE")).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CANCELLED\"}")).andExpect(status().isOk());
    }
    @Test void catalogIsCompleteAndOwned() throws Exception {
        mvc.perform(get("/api/v1/recruiters/jobs").header("Authorization",token(recruiter,"RECRUITER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].offer.description").value("Description"))
            .andExpect(jsonPath("$.content[0].candidate_count").value(1));
        mvc.perform(get("/api/v1/recruiters/jobs/"+job).header("Authorization",token(outsider,"RECRUITER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/recruiters/jobs/"+job).header("Authorization",token(candidate,"CANDIDATE"))).andExpect(status().isForbidden());
    }
    @Test void privateSettingsPasswordChangeAndTokenRevocation() throws Exception {
        String bearer=token(candidate,"CANDIDATE");
        jdbc.update("UPDATE users SET password_hash=? WHERE id=?",encoder.encode("OriginalPassword123!"),candidate);
        mvc.perform(patch("/api/v1/account/settings").header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"+33 6 12 34 56 78\",\"domains\":[\"Data\"]}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/account").header("Authorization",bearer)).andExpect(jsonPath("$.settings.domains[0]").value("Data"))
            .andExpect(jsonPath("$.settings.phone").value("+33612345678"));
        mvc.perform(patch("/api/v1/account/settings").header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"0612345678\"}")).andExpect(status().isBadRequest());
        org.junit.jupiter.api.Assertions.assertEquals("+33612345678",jdbc.queryForObject("SELECT phone FROM candidate_profiles WHERE user_id=?",String.class,candidate));
        mvc.perform(patch("/api/v1/account/credentials").header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"wrong\",\"newPassword\":\"UpdatedPassword123!\"}")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/account/credentials").header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"OriginalPassword123!\",\"newPassword\":\"UpdatedPassword123!\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/account").header("Authorization",bearer)).andExpect(status().isUnauthorized());
    }
    @Test void anonymousRequestsCannotAccessNewResources() throws Exception {
        mvc.perform(get("/api/v1/account")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/interviews")).andExpect(status().isUnauthorized());
    }
    @Test void administratorCanSuspendAndReactivateWithAudit() throws Exception {
        String body="{\"status\":\"SUSPENDED\",\"reason\":\"Account review\"}";
        mvc.perform(patch("/api/v1/admin/users/"+candidate+"/status").header("Authorization",token(recruiter,"RECRUITER")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/admin/users/"+candidate+"/status").header("Authorization",token(admin,"ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/account").header("Authorization",token(candidate,"CANDIDATE"))).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/admin/users/"+candidate+"/status").header("Authorization",token(admin,"ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}")).andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals(2L,jdbc.queryForObject("SELECT count(*) FROM account_status_audit WHERE target_user_id=?",Long.class,candidate));
    }
}
