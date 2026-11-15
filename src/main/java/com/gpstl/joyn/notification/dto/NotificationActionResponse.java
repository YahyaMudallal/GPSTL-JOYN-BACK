package com.gpstl.joyn.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Résultat d'une action sur les notifications (marquer comme lues, suppression, purge).
 */
@Schema(description = "Confirmation d'une action effectuée sur les notifications")
public record NotificationActionResponse(
        @Schema(description = "Message descriptif du résultat", example = "Notifications supprimées avec succès")
        String message,

        @Schema(description = "Nombre de notifications affectées par l'opération", example = "5")
        int affectedCount
) {
}
