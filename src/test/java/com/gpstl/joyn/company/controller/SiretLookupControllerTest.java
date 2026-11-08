package com.gpstl.joyn.company.controller;

import com.gpstl.joyn.company.service.SiretLookupService;
import com.gpstl.joyn.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SiretLookupController.class)
@Import({SecurityConfig.class, SiretLookupService.class, SiretLookupControllerTest.RegistryConfig.class})
class SiretLookupControllerTest {
    private static final String URL = "/api/v1/companies/lookup?siret=35600000000048";
    @Autowired private MockRestServiceServer server;
    @Autowired private MockMvc mvc;

    @BeforeEach
    void setUp() {
        server.reset();
    }

    @Test
    void returnsFrontendFieldsAndExplicitNullsWithoutAuthentication() throws Exception {
        server.expect(requestTo("https://registry.test/search?q=35600000000048"))
                .andRespond(withSuccess("""
                        {"results":[{"siren":"356000000","nom_complet":"LA POSTE",
                          "etat_administratif":"A", "siege":{
                            "siret":"35600000000048","libelle_commune":"PARIS","etat_administratif":"A"
                          }}]}
                        """, MediaType.APPLICATION_JSON));
        mvc.perform(get(URL).header("Origin", "http://localhost:3000"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(jsonPath("$.siret").value("35600000000048"))
                .andExpect(jsonPath("$.legalName").value("LA POSTE"))
                .andExpect(jsonPath("$.displayName").value("LA POSTE"))
                .andExpect(jsonPath("$.city").value("PARIS"))
                .andExpect(jsonPath("$.establishmentActive").value(true))
                .andExpect(jsonPath("$.emailDomain").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"activity\":null")));
        server.verify();
    }

    @Test
    void malformedSiretReturns400WithStableError() throws Exception {
        mvc.perform(get("/api/v1/companies/lookup").param("siret", "123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SIRET"))
                .andExpect(jsonPath("$.message").isString());
        server.verify();
    }

    @Test
    void missingSiretParameterReturns400WithStableError() throws Exception {
        mvc.perform(get("/api/v1/companies/lookup"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SIRET"));
        server.verify();
    }

    @Test
    void noResultReturns404WithStableError() throws Exception {
        server.expect(requestTo("https://registry.test/search?q=35600000000048"))
                .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));
        mvc.perform(get(URL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SIRET_NOT_FOUND"));
        server.verify();
    }

    @Test
    void upstreamRateLimitReturns503RatherThan404() throws Exception {
        server.expect(requestTo("https://registry.test/search?q=35600000000048"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        mvc.perform(get(URL))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SIRET_SERVICE_UNAVAILABLE"));
        server.verify();
    }

    @Test
    void allowsBrowserPreflight() throws Exception {
        mvc.perform(options(URL).header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    @Test
    void existingProtectedCompanyUpdateStillRequiresAuthentication() throws Exception {
        mvc.perform(patch("/api/v1/companies/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Other\"}"))
                .andExpect(status().isUnauthorized());
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class RegistryConfig {
        @Bean
        RestClient.Builder registryBuilder() {
            return RestClient.builder().baseUrl("https://registry.test");
        }

        @Bean
        MockRestServiceServer registryServer(RestClient.Builder registryBuilder) {
            return MockRestServiceServer.bindTo(registryBuilder).build();
        }

        @Bean
        RestClient companyLookupRestClient(RestClient.Builder registryBuilder, MockRestServiceServer server) {
            return registryBuilder.build();
        }
    }
}
