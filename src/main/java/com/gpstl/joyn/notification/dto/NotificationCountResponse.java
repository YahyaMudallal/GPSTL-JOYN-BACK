package com.gpstl.joyn.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Compteur rapide des notifications non lues (pour pastille / badge en interface).
 */
@Schema(description = "Compteur de notifications non lues")
public record NotificationCountResponse(
        @Schema(description = "Nombre de notifications non lues", example = "4")
        long unreadCount
) {
}
