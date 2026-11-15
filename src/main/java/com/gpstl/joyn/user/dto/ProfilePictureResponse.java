package com.gpstl.joyn.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Réponse renvoyée lors de la consultation, l'upload ou la mise à jour d'une photo de profil.
 * Fournit à la fois profile_picture_url et photo_url pour une interopérabilité maximale.
 */
public record ProfilePictureResponse(
        @JsonProperty("profile_picture_url") String profilePictureUrl,
        @JsonProperty("photo_url") String photoUrl
) {
    public static ProfilePictureResponse of(String url) {
        return new ProfilePictureResponse(url, url);
    }
}
