package com.gpstl.joyn.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Réponse paginée contenant la liste des notifications et les compteurs associés.
 */
@Schema(description = "Réponse paginée contenant les notifications de l'utilisateur")
public record NotificationListResponse(
        @Schema(description = "Liste des notifications pour la page demandée")
        List<NotificationItem> notifications,

        @Schema(description = "Nombre total de notifications non lues", example = "3")
        long unreadCount,

        @Schema(description = "Nombre total de notifications existantes", example = "12")
        long totalCount,

        @Schema(description = "Numéro de page actuelle (base 1)", example = "1")
        int page,

        @Schema(description = "Taille maximale de la page", example = "20")
        int pageSize,

        @Schema(description = "Nombre total de pages disponibles", example = "1")
        int totalPages
) {
}
