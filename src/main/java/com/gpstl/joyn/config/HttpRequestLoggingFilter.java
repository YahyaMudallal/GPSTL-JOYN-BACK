package com.gpstl.joyn.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtre HTTP pour journaliser les requêtes entrantes et leur traitement dans le terminal.
 * Affiche la méthode, l'URL, l'IP cliente, le code de statut HTTP et la durée d'exécution.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        long startTime = System.currentTimeMillis();
        String method = request.getMethod();
        String fullPath = buildFullPath(request);
        String clientIp = resolveClientIp(request);

        log.info("[HTTP-IN]  {} {} - IP: {}", method, fullPath, clientIp);

        try {
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.error("[HTTP-OUT] {} {} -> EXCEPTION après {} ms : {}",
                    method, fullPath, durationMs, ex.getMessage(), ex);
            throw ex;
        }

        long durationMs = System.currentTimeMillis() - startTime;
        int status = response.getStatus();

        if (status >= 500) {
            log.error("[HTTP-OUT] {} {} -> Statut: {} ({} ms)", method, fullPath, status, durationMs);
        } else if (status >= 400) {
            log.warn("[HTTP-OUT] {} {} -> Statut: {} ({} ms)", method, fullPath, status, durationMs);
        } else {
            log.info("[HTTP-OUT] {} {} -> Statut: {} ({} ms)", method, fullPath, status, durationMs);
        }
    }

    private String buildFullPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        return queryString != null ? uri + "?" + queryString : uri;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
