package com.gpstl.joyn.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Politique CORS commune à l'ensemble de l'API.
 *
 * Les motifs locaux acceptent les ports variables des serveurs de développement,
 * tandis que l'origine de production reste explicitement limitée au frontend Joyn.
 */
@Configuration
public class CorsConfig {

    private final List<String> allowedOriginPatterns;

    public CorsConfig(@Value("${joyn.cors.allowed-origin-patterns}") List<String> allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Les ports locaux sont variables ; le motif précis de production est configuré séparément.
        configuration.setAllowedOriginPatterns(allowedOriginPatterns);
        // Garder les méthodes de l'API explicites évite d'ouvrir des verbes inutilisés.
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
        // Authorization permet au frontend d'envoyer le JWT Bearer ; aucun cookie cross-origin n'est nécessaire.
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"));
        configuration.setMaxAge(3600L);

        // Une seule politique centralisée pour toutes les routes, pré-requêtes incluses.
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
