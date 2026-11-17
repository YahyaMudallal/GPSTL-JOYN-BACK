package com.gpstl.joyn.additional;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountTokenFilterTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private Jwt token(String subject, String version) {
        var builder=Jwt.withTokenValue("test-token").header("alg","HS256").subject(subject).claim("role","RECRUITER")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300));
        if(version!=null) builder.claim("sessionVersion",version);
        return builder.build();
    }
    @Test void unrevokedTokensKeepEndpointAuthorizationAndJsonContracts() throws Exception {
        for(String state:List.of("ACTIVE","SUSPENDED","CHANGED_ROLE","MISSING")) {
            var jdbc=mock(JdbcTemplate.class);var chain=mock(FilterChain.class);
            var row=new HashMap<String,Object>();row.put("tokens_valid_after",null);
            // Status/role are intentionally outside this filter: route services own those checks.
            row.put("status",state);row.put("role","CANDIDATE");
            when(jdbc.queryForList("SELECT tokens_valid_after FROM users WHERE id=?",12L))
                .thenReturn(state.equals("MISSING")?List.of():List.of(row));
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token("12",null)));
            var request=new MockHttpServletRequest();var response=new MockHttpServletResponse();
            new AccountTokenFilter(jdbc).doFilter(request,response,chain);
            verify(chain).doFilter(request,response);
        }
    }
    @Test void malformedSubjectIsHandledByTheExistingEndpoint() throws Exception {
        var jdbc=mock(JdbcTemplate.class);var chain=mock(FilterChain.class);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token("not-a-user-id",null)));
        var request=new MockHttpServletRequest();var response=new MockHttpServletResponse();
        new AccountTokenFilter(jdbc).doFilter(request,response,chain);
        verify(chain).doFilter(request,response);verifyNoInteractions(jdbc);
    }
    @Test void revokedTokenGetsJson401ButFreshSessionVersionPasses() throws Exception {
        Instant version=Instant.parse("2026-11-17T20:00:00Z");
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList("SELECT tokens_valid_after FROM users WHERE id=?",12L))
            .thenReturn(List.of(Map.of("tokens_valid_after",java.sql.Timestamp.from(version))));
        var chain=mock(FilterChain.class);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token("12","old")));
        var request=new MockHttpServletRequest();var response=new MockHttpServletResponse();
        new AccountTokenFilter(jdbc).doFilter(request,response,chain);
        assertEquals(401,response.getStatus());assertTrue(response.getContentAsString().contains("\"status\":401"));
        verifyNoInteractions(chain);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token("12",version.toString())));
        new AccountTokenFilter(jdbc).doFilter(new MockHttpServletRequest(),new MockHttpServletResponse(),chain);
        verify(chain).doFilter(any(),any());
    }
}
