package com.gpstl.joyn.notification.dto;

import com.gpstl.joyn.notification.model.Notification;
import com.gpstl.joyn.notification.model.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * Représentation d'une notification pour les réponses de l'API REST.
 */
@Schema(description = "Objet représentant une notification reçue par l'utilisateur")
public record NotificationItem(
        @Schema(description = "Identifiant unique de la notification", example = "42")
        Long id,

        @Schema(description = "Type de l'événement notifié", example = "APPLICATION_UPDATE")
        NotificationType type,

        @Schema(description = "Titre de la notification", example = "Mise à jour de votre candidature")
        String title,

        @Schema(description = "Contenu ou extrait textuel de la notification", example = "Votre candidature pour « Dev Java » chez Acme est passée au statut : Entretien.")
        String message,

        @Schema(description = "Lien ou chemin vers la ressource concernée", example = "/applications/12")
        String link,

        @Schema(description = "Indique si la notification a déjà été consultée/lue", example = "false")
        boolean isRead,

        @Schema(description = "Date et heure de consultation/lecture", example = "2026-11-15T10:30:00Z")
        OffsetDateTime readAt,

        @Schema(description = "Date et heure de création", example = "2026-11-15T10:00:00Z")
        OffsetDateTime createdAt
) {
    public static NotificationItem from(Notification notification) {
        return new NotificationItem(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getLink(),
                notification.isRead(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}
