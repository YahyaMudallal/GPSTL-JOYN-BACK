package com.gpstl.joyn.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    private static final String DEPLOYED_FRONTEND =
            "https://gpstl-joyn-front.joynpstl.workers.dev";

    private final CorsConfigurationSource source = new CorsConfig(List.of(
            "http://localhost:[*]",
            "http://127.0.0.1:[*]",
            DEPLOYED_FRONTEND
    )).corsConfigurationSource();

    @Test
    void allowsLocalAndDeployedFrontendsForApiRequests() {
        assertThat(configurationFor("/api/v1/candidates/profile")
                .checkOrigin("http://localhost:5173")).isEqualTo("http://localhost:5173");
        assertThat(configurationFor("/api/v1/auth/login")
                .checkOrigin("http://127.0.0.1:3000")).isEqualTo("http://127.0.0.1:3000");
        assertThat(configurationFor("/api/v1/auth/login")
                .checkOrigin(DEPLOYED_FRONTEND)).isEqualTo(DEPLOYED_FRONTEND);
    }

    @Test
    void rejectsUnconfiguredOriginsAndAllowsPreflightHeadersAndMethods() {
        CorsConfiguration configuration = configurationFor("/api/v1/candidates/profile");

        assertThat(configuration.checkOrigin("https://malicious.example")).isNull();
        assertThat(configuration.checkHttpMethod(org.springframework.http.HttpMethod.PATCH)).isNotNull();
        assertThat(configuration.checkHeaders(List.of("Authorization", "Content-Type")))
                .containsExactlyInAnyOrder("Authorization", "Content-Type");
    }

    private CorsConfiguration configurationFor(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", path);
        return source.getCorsConfiguration(request);
    }
}
