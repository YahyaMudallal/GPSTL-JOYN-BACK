package com.gpstl.joyn.additional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

/** Checks explicit session revocation only. Route services retain their account/role/error contracts. */
public class AccountTokenFilter extends OncePerRequestFilter {
    private final JdbcTemplate jdbc;
    public AccountTokenFilter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            Long id = null;
            try { id = Long.valueOf(jwt.getSubject()); }
            catch (NumberFormatException ignored) {
                // Controllers provide the existing JSON error for malformed authenticated identities.
            }
            if (id != null) {
                var rows = jdbc.queryForList("SELECT tokens_valid_after FROM users WHERE id=?", id);
                if (!rows.isEmpty()) {
                    Object cutoff = rows.getFirst().get("tokens_valid_after");
                    java.time.Instant version = cutoff instanceof java.sql.Timestamp t ? t.toInstant()
                        : cutoff instanceof java.time.OffsetDateTime t ? t.toInstant()
                        : cutoff instanceof java.time.Instant t ? t : null;
                    if (version != null && !version.toString().equals(jwt.getClaimAsString("sessionVersion"))) {
                        SecurityContextHolder.clearContext();
                        response.setStatus(401);
                        response.setContentType("application/json");
                        response.setCharacterEncoding("UTF-8");
                        response.getWriter().write("{\"status\":401,\"message\":\"Session expired. Sign in again.\"}");
                        return;
                    }
                }
                // Missing/suspended accounts and changed roles are checked by the endpoint's
                // existing service, preserving its 401/403 status and JSON response.
            }
        }
        chain.doFilter(request, response);
    }
}
