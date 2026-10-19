package com.gpstl.joyn.resume.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;

/**
 * GeminiConfig
 * 
 * Configuration class for setting up the Gemini client used for resume parsing.
 */
@Configuration
public class GeminiConfig {

    @Bean
    public ObjectMapper resumeObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @Lazy
    public Client geminiClient(
        @Value("${spring.gemini.api-key:}") String apiKey
    ) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException(
                "Gemini is not configured. Set the GOOGLE_API_KEY environment variable."
            );
        }

        return Client.builder()
            .apiKey(apiKey)
            .build();
    }
}