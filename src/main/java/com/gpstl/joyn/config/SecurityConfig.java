
package com.gpstl.joyn.config;

import com.gpstl.joyn.common.repository.UserRepository;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource
    ) throws Exception {

        http
                .cors(cors ->
                        cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )
                .authorizeHttpRequests(auth -> auth

                        .requestMatchers("/api/v1/auth/**")
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/companies/lookup"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/recruiters/register"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/candidates/applications"
                        ).hasRole("CANDIDATE")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/candidates/recommendations"
                        ).hasRole("CANDIDATE")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/applications"
                        ).hasRole("CANDIDATE")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/candidates/resumes/**"
                        ).hasAnyRole(
                                "CANDIDATE",
                                "RECRUITER",
                                "ADMIN"
                        )

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/applications/*/status"
                        ).hasAnyRole(
                                "RECRUITER",
                                "ADMIN"
                        )

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/recruiters/jobs/*/recommendations"
                        ).hasRole("RECRUITER")

                        .requestMatchers("/api/v1/recruiters/**")
                        .hasRole("RECRUITER")

                        .requestMatchers("/api/v1/admin/**")
                        .hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/companies/*"
                        ).hasRole("RECRUITER")

                        .requestMatchers(
                                "/api/v1/candidates/profile",
                                "/api/v1/candidates/profile/**"
                        ).hasRole("CANDIDATE")

                        .requestMatchers(
                                "/api/v1/candidates/dashboard",
                                "/api/v1/candidates/dashboard/**"
                        ).hasRole("CANDIDATE")

                        .requestMatchers(
                                "/api/v1/candidates/*/profile"
                        ).hasAnyRole("RECRUITER", "ADMIN")

                        // A67: user account deletion.
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/v1/users/*"
                        ).authenticated()

                        // Preserve existing team routes.
                        .anyRequest().permitAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2

                        .jwt(jwt ->
                                jwt.jwtAuthenticationConverter(
                                        jwtAuthenticationConverter()
                                )
                        )

                        // Return JSON when JWT authentication fails.
                        .authenticationEntryPoint(
                                (request, response, exception) -> {

                                    // Preserve the standard Bearer
                                    // authentication response headers.
                                    new BearerTokenAuthenticationEntryPoint()
                                            .commence(
                                                    request,
                                                    response,
                                                    exception
                                            );

                                    String details =
                                            exception.getMessage() == null
                                                    ? ""
                                                    : exception.getMessage();

                                    String message = "Unauthorized";

                                    if (details.contains(
                                            "Invalid user identifier")) {

                                        message =
                                                "Invalid authenticated user identity";

                                    } else if (details.contains(
                                            "Account is deleted or does not exist")) {

                                        message =
                                                "Authenticated user does not exist";
                                    }

                                    response.setStatus(401);
                                    response.setContentType(
                                            MediaType.APPLICATION_JSON_VALUE
                                    );
                                    response.setCharacterEncoding("UTF-8");

                                    String json = """
                                            {
                                              "status": 401,
                                              "error": "Unauthorized",
                                              "message": "%s",
                                              "errors": []
                                            }
                                            """.formatted(message);

                                    response.getWriter().write(json);
                                }
                        )
                );

        return http.build();
    }

    /**
     * JWT validation.
     *
     * Deleted and missing users cannot authenticate.
     * Suspended users remain subject to existing
     * application-level authorization checks.
     */
    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${joyn.security.jwt.secret}") String secret,
            ObjectProvider<UserRepository> userRepositoryProvider) {

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(secretKey(secret))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        var defaultValidator = JwtValidators.createDefault();

        decoder.setJwtValidator(jwt -> {

            var result = defaultValidator.validate(jwt);

            if (result.hasErrors()) {
                return result;
            }

            UserRepository userRepository =
                    userRepositoryProvider.getIfAvailable();

            // Never accept a JWT when account verification
            // is unavailable.
            if (userRepository == null) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error(
                                "invalid_token",
                                "Account verification unavailable",
                                null
                        )
                );
            }

            try {
                Long userId = Long.valueOf(jwt.getSubject());

                boolean deletedOrMissing = userRepository
                        .findById(userId)
                        .map(user ->
                                "DELETED".equals(user.getStatus())
                        )
                        .orElse(true);

                if (deletedOrMissing) {
                    return OAuth2TokenValidatorResult.failure(
                            new OAuth2Error(
                                    "invalid_token",
                                    "Account is deleted or does not exist",
                                    null
                            )
                    );
                }

            } catch (NumberFormatException exception) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error(
                                "invalid_token",
                                "Invalid user identifier",
                                null
                        )
                );
            }

            return OAuth2TokenValidatorResult.success();
        });

        return decoder;
    }

    /**
     * Converts JWT roles to Spring Security authorities.
     *
     * Example: RECRUITER -> ROLE_RECRUITER
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtGrantedAuthoritiesConverter authorities =
                new JwtGrantedAuthoritiesConverter();

        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(authorities);

        return converter;
    }

    /**
     * Builds the HMAC-SHA256 secret key.
     */
    public static SecretKey secretKey(String secret) {

        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);

        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "joyn.security.jwt.secret doit faire au moins 32 caractères"
            );
        }

        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
