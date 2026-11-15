package com.gpstl.joyn.user.controller;

import com.gpstl.joyn.user.dto.ProfilePictureResponse;
import com.gpstl.joyn.user.exception.UserProfilePictureException;
import com.gpstl.joyn.user.service.UserProfilePictureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;

/**
 * Endpoint unifié de gestion de la photo de profil pour tous les utilisateurs (candidat, recruteur, admin).
 */
@RestController
@RequestMapping("/api/v1/users/profile-picture")
@Tag(name = "Users", description = "Gestion des paramètres et ressources utilisateurs")
@SecurityRequirement(name = "bearerAuth")
@CrossOrigin(origins = "*")
public class UserProfilePictureController {

    private final UserProfilePictureService service;

    public UserProfilePictureController(UserProfilePictureService service) {
        this.service = service;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Obtenir l'URL de la photo de profil de l'utilisateur connecté",
            description = "Renvoie l'URL signée ou accessible de la photo de profil actuelle de l'utilisateur connecté.")
    @ApiResponse(responseCode = "200", description = "URL de la photo de profil")
    @ApiResponse(responseCode = "401", description = "Authentification requise")
    @ApiResponse(responseCode = "403", description = "Compte inactif ou suspendu")
    @ApiResponse(responseCode = "404", description = "Utilisateur introuvable")
    public ResponseEntity<ProfilePictureResponse> getProfilePicture(Principal principal) {
        long userId = extractUserId(principal);
        return ResponseEntity.ok(service.getProfilePicture(userId));
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Uploader ou remplacer la photo de profil de l'utilisateur connecté",
            description = "Valide l'image, la recadre en 1:1, la redimensionne en 512x512 px et l'enregistre en WebP 80% sur Supabase.")
    @ApiResponse(responseCode = "200", description = "Photo de profil mise à jour avec succès")
    @ApiResponse(responseCode = "400", description = "Fichier non valide ou dimensions incorrectes")
    @ApiResponse(responseCode = "401", description = "Authentification requise")
    @ApiResponse(responseCode = "403", description = "Compte inactif ou suspendu")
    public ResponseEntity<ProfilePictureResponse> updateProfilePicture(
            @Parameter(description = "Fichier image à uploader")
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "photo", required = false) MultipartFile photo,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "picture", required = false) MultipartFile picture,
            Principal principal) {

        MultipartFile uploadedFile = resolveFile(file, photo, image, picture);
        long userId = extractUserId(principal);
        ProfilePictureResponse response = service.updateProfilePicture(userId, uploadedFile);
        return ResponseEntity.ok(response);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Alias POST pour l'upload ou le remplacement de la photo de profil")
    public ResponseEntity<ProfilePictureResponse> uploadProfilePicturePost(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "photo", required = false) MultipartFile photo,
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "picture", required = false) MultipartFile picture,
            Principal principal) {
        return updateProfilePicture(file, photo, image, picture, principal);
    }

    @DeleteMapping
    @Operation(summary = "Supprimer la photo de profil de l'utilisateur connecté",
            description = "Supprime le fichier du serveur de stockage Supabase et vide l'URL de profil.")
    @ApiResponse(responseCode = "204", description = "Photo de profil supprimée")
    @ApiResponse(responseCode = "401", description = "Authentification requise")
    @ApiResponse(responseCode = "403", description = "Compte inactif ou suspendu")
    public ResponseEntity<Void> deleteProfilePicture(Principal principal) {
        long userId = extractUserId(principal);
        service.deleteProfilePicture(userId);
        return ResponseEntity.noContent().build();
    }

    private static MultipartFile resolveFile(MultipartFile file, MultipartFile photo, MultipartFile image, MultipartFile picture) {
        if (file != null && !file.isEmpty()) return file;
        if (photo != null && !photo.isEmpty()) return photo;
        if (image != null && !image.isEmpty()) return image;
        if (picture != null && !picture.isEmpty()) return picture;
        throw new UserProfilePictureException(400, "Veuillez fournir un fichier image valide (paramètre 'file', 'photo' ou 'picture').");
    }

    private static long extractUserId(Principal principal) {
        if (principal == null) {
            throw new UserProfilePictureException(401, "L'authentification est requise.");
        }
        try {
            long id = Long.parseLong(principal.getName());
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new UserProfilePictureException(401, "Le jeton d'authentification ne contient pas un identifiant utilisateur valide.");
        }
    }
}
