package com.gpstl.joyn.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpRequestLoggingFilterTest {

    private HttpRequestLoggingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new HttpRequestLoggingFilter();
    }

    @Test
    @DisplayName("Devrait exécuter la chaîne de filtres pour une requête HTTP 200 avec succès")
    void shouldLogAndProceedSuccessfully() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/jobs");
        request.setQueryString("page=0&size=10");
        request.setRemoteAddr("192.168.1.10");

        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Devrait gérer une requête avec code HTTP 400")
    void shouldHandle4xxStatusCode() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(400);

        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("Devrait gérer une requête avec code HTTP 500")
    void shouldHandle5xxStatusCode() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/broken");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);

        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(500);
    }

    @Test
    @DisplayName("Devrait extraire l'IP cliente depuis le header X-Forwarded-For")
    void shouldExtractClientIpFromXForwardedFor() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/status");
        request.addHeader("X-Forwarded-For", "203.0.113.195, 70.41.3.18");
        request.setRemoteAddr("10.0.0.1");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Devrait propager les exceptions levées par la chaîne de filtres")
    void shouldPropagateExceptions() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/crash");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain throwingChain = (req, res) -> {
            throw new ServletException("Simulated chain failure");
        };

        assertThatThrownBy(() -> filter.doFilterInternal(request, response, throwingChain))
                .isInstanceOf(ServletException.class)
                .hasMessage("Simulated chain failure");
    }
}
