package com.gpstl.joyn.notification.controller;

import com.gpstl.joyn.joboffer.dto.ApiError;
import com.gpstl.joyn.notification.dto.NotificationActionResponse;
import com.gpstl.joyn.notification.dto.NotificationCountResponse;
import com.gpstl.joyn.notification.dto.NotificationItem;
import com.gpstl.joyn.notification.dto.NotificationListResponse;
import com.gpstl.joyn.notification.exception.NotificationException;
import com.gpstl.joyn.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Point d'entrée REST pour la gestion des notifications utilisateurs (candidats et recruteurs).
 *
 *  GET    /api/v1/notifications              mes notifications (paginées, filtre non lues, option de purge)
 *  GET    /api/v1/notifications/unread-count nombre de notifications non lues (pour badge)
 *  PATCH  /api/v1/notifications/{id}/read     marquer comme lue (avec option ?delete=true)
 *  PATCH  /api/v1/notifications/read-all      marquer toutes les notifications comme lues
 *  DELETE /api/v1/notifications/{id}          supprimer une notification
 *  DELETE /api/v1/notifications/read          supprimer toutes les notifications déjà lues
 *  DELETE /api/v1/notifications               supprimer toutes les notifications
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "Gestion des notifications pour candidats et recruteurs")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(
            summary = "Lister les notifications de l'utilisateur connecté",
            description = "Récupère les notifications d'un candidat ou d'un recruteur, triées par date décroissante. "
                    + "Permet de filtrer uniquement les notifications non lues, et de purger automatiquement "
                    + "les notifications déjà consultées via le paramètre autoDeleteConsulted.")
    @ApiResponse(responseCode = "200", description = "Liste paginée des notifications et compteurs")
    @ApiResponse(responseCode = "400", description = "Paramètre de pagination invalide",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Authentification requise",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<NotificationListResponse> getNotifications(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Numéro de page (base 1)", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Nombre d'éléments par page (1 à 50)", example = "20")
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "Ne renvoyer que les notifications non lues", example = "false")
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @Parameter(description = "Supprime les notifications déjà consultées / lues", example = "false")
            @RequestParam(defaultValue = "false") boolean autoDeleteConsulted) {

        return ResponseEntity.ok(notificationService.getNotifications(
                userId(jwt),
                page,
                limit,
                unreadOnly,
                autoDeleteConsulted));
    }

    @GetMapping("/unread-count")
    @Operation(
            summary = "Nombre de notifications non lues",
            description = "Retourne rapidement le nombre de notifications non lues pour l'affichage d'un badge dans l'interface.")
    @ApiResponse(responseCode = "200", description = "Compteur de notifications non lues")
    @ApiResponse(responseCode = "401", description = "Authentification requise",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<NotificationCountResponse> getUnreadCount(
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(notificationService.getUnreadCount(userId(jwt)));
    }

    @PatchMapping("/{id}/read")
    @Operation(
            summary = "Marquer une notification comme consultée / lue",
            description = "Marque la notification identifiée par son ID comme lue. "
                    + "Si delete=true est passé en paramètre, la notification est immédiatement supprimée après consultation.")
    @ApiResponse(responseCode = "200", description = "Notification mise à jour ou supprimée")
    @ApiResponse(responseCode = "401", description = "Authentification requise",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Notification introuvable",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<NotificationItem> markAsRead(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Identifiant de la notification", example = "42")
            @PathVariable("id") Long id,
            @Parameter(description = "Supprimer immédiatement la notification au lieu de la conserver lue", example = "false")
            @RequestParam(defaultValue = "false") boolean delete) {

        return ResponseEntity.ok(notificationService.markAsRead(userId(jwt), positive(id), delete));
    }

    @PatchMapping("/{id}/unread")
    public ResponseEntity<NotificationItem> markUnread(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markUnread(userId(jwt), id));
    }

    @PatchMapping("/read-all")
    @Operation(
            summary = "Marquer toutes les notifications comme lues",
            description = "Marque l'ensemble des notifications non lues de l'utilisateur comme lues.")
    @ApiResponse(responseCode = "200", description = "Toutes les notifications ont été marquées comme lues")
    @ApiResponse(responseCode = "401", description = "Authentification requise",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<NotificationActionResponse> markAllAsRead(
            @AuthenticationPrincipal Jwt jwt) {

        int affected = notificationService.markAllAsRead(userId(jwt));
        return ResponseEntity.ok(new NotificationActionResponse(
                "Toutes les notifications ont été marquées comme lues",
                affected));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Supprimer une notification spécifique",
            description = "Supprime définitivement une notification de l'utilisateur.")
    @ApiResponse(responseCode = "200", description = "Notification supprimée avec succès")
    @ApiResponse(responseCode = "401", description = "Authentification requise",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Notification introuvable",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<NotificationActionResponse> deleteNotification(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Identifiant de la notification", example = "42")
            @PathVariable("id") Long id) {

        notificationService.deleteNotification(userId(jwt), positive(id));
        return ResponseEntity.ok(new NotificationActionResponse(
                "Notification supprimée avec succès",
                1));
    }

    @DeleteMapping("/read")
    @Operation(
            summary = "Supprimer toutes les notifications lues / consultées",
            description = "Purger toutes les notifications déjà consultées pour faire de la place.")
    @ApiResponse(responseCode = "200", description = "Notifications lues purgées")
    @ApiResponse(responseCode = "401", description = "Authentification requise",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<NotificationActionResponse> deleteReadNotifications(
            @AuthenticationPrincipal Jwt jwt) {

        int affected = notificationService.deleteReadNotifications(userId(jwt));
        return ResponseEntity.ok(new NotificationActionResponse(
                "Notifications lues supprimées avec succès",
                affected));
    }

    @DeleteMapping
    @Operation(
            summary = "Supprimer toutes les notifications de l'utilisateur",
            description = "Vide complètement la boîte de notifications de l'utilisateur connecté.")
    @ApiResponse(responseCode = "200", description = "Toutes les notifications ont été supprimées")
    @ApiResponse(responseCode = "401", description = "Authentification requise",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<NotificationActionResponse> deleteAllNotifications(
            @AuthenticationPrincipal Jwt jwt) {

        int affected = notificationService.deleteAllNotifications(userId(jwt));
        return ResponseEntity.ok(new NotificationActionResponse(
                "Toutes les notifications ont été supprimées avec succès",
                affected));
    }

    // ------------------------------------------------------------------ Outils

    /**
     * Extrait l'identifiant utilisateur (users.id) à partir du claim "sub" du jeton JWT.
     */
    private static long userId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw NotificationException.unauthorized("Authentication is required");
        }
        try {
            long id = Long.parseLong(jwt.getSubject());
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException ex) {
            throw NotificationException.unauthorized("Invalid authenticated user identity");
        }
    }

    private static long positive(Long id) {
        if (id == null || id <= 0) {
            throw NotificationException.badRequest("id", "id must be a positive integer");
        }
        return id;
    }
}
