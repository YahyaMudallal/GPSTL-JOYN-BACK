package com.gpstl.joyn.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Sécurité de l'API.
 *
 * Contrat attendu des tokens JWT (à respecter par la route de connexion) :
 *  - signés en HS256 avec la clé joyn.security.jwt.secret ;
 *  - claim "sub"  = id de l'utilisateur dans la table users (ex. "42") ;
 *  - claim "role" = CANDIDATE, RECRUITER ou ADMIN (comme la colonne users.role).
 *
 * Seules les routes /api/v1/recruiters/**, /api/v1/candidates/profile et /api/v1/admin/candidates/**
 * sont protégées pour l'instant, afin de ne pas
 * bloquer les autres routes de l'équipe (inscription, connexion...). L'équipe pourra
 * resserrer les règles quand toutes les routes existeront.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/recruiters/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/applications").hasRole("CANDIDATE")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/applications/*/status")
                                .hasAnyRole("RECRUITER", "ADMIN")
                        .requestMatchers("/api/v1/recruiters/**").hasRole("RECRUITER")
                        // Profil du candidat connecté ; /candidates/register reste public.
                        .requestMatchers("/api/v1/candidates/profile", "/api/v1/candidates/profile/**").hasRole("CANDIDATE")
                        // Intervention d'un administrateur sur un profil candidat désigné.
                        .requestMatchers("/api/v1/admin/candidates/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                // Sans token -> 401 ; token valide sans le rôle -> 403
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder(@Value("${joyn.security.jwt.secret}") String secret) {
        return NimbusJwtDecoder.withSecretKey(secretKey(secret))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /** Transforme le claim "role": "RECRUITER" en autorité Spring "ROLE_RECRUITER". */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /** Clé HMAC-SHA256 construite à partir du secret (32 caractères minimum). */
    public static SecretKey secretKey(String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("joyn.security.jwt.secret doit faire au moins 32 caractères");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
