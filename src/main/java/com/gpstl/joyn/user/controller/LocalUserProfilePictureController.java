package com.gpstl.joyn.user.controller;

import com.gpstl.joyn.user.service.UserProfilePictureStorage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Contrôleur de distribution locale des photos de profil pour le développement sans Supabase.
 */
@RestController
@RequestMapping("/api/v1/users/profile-pictures")
@ConditionalOnProperty(name = "joyn.storage.provider", havingValue = "local")
@Tag(name = "Profile Pictures", description = "Distribution locale des photos de profil utilisateur pour le développement")
public class LocalUserProfilePictureController {

    private final UserProfilePictureStorage storage;

    public LocalUserProfilePictureController(UserProfilePictureStorage storage) {
        this.storage = storage;
    }

    @GetMapping("/{userId}/{fileName}")
    @Operation(summary = "Récupérer une photo de profil en mode local",
            description = "Sert le fichier WebP stocké localement.")
    public ResponseEntity<Resource> getProfilePicture(
            @PathVariable Long userId,
            @PathVariable String fileName) {
        String key = "profile_pictures/" + userId + "/" + fileName;
        UserProfilePictureStorage.StoredProfilePicture picture = storage.load(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo de profil introuvable"));

        return ResponseEntity.ok()
                .contentType(picture.mediaType())
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(picture.resource());
    }
}
