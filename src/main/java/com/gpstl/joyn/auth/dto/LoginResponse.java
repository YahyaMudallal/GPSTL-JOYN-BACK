package com.gpstl.joyn.auth.dto;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 
 * Réponse 200 de POST /api/v1/auth/login.
 * Exemple: {"access_token" : "eyJ...", "token_type": "Bearer", "expires_in":3600, "role": "ADMIN"}
 * 
 * Le front garde access_token et l'envoie ensuite dans l'en-tete
 * "Authorization: Bearer < access_token>" de chaque requete protégée.
*/

public record LoginResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("token_type") String tokenType,
    @JsonProperty("expires_in") long expiresIn,
    String role // le rôle de l’utilisateur
){}