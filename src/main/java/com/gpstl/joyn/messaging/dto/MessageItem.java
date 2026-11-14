package com.gpstl.joyn.messaging.dto;

import java.time.OffsetDateTime;

/**
 * Un message. Mêmes noms que ceux lus par le front (id, text, createdAt, fromMe).
 *
 * @param senderRole CANDIDATE ou RECRUITER (null si le compte de l'expéditeur a été supprimé)
 * @param fromMe     true si c'est l'utilisateur connecté qui l'a envoyé
 * @param readAt     date de lecture par le destinataire, null si pas encore lu
 * @param clientId   identifiant choisi par le front à l'envoi (renvoyé tel quel), null sinon
 */
public record MessageItem(
        long id,
        long conversationId,
        Long senderUserId,
        String senderRole,
        String senderName,
        boolean fromMe,
        String text,
        OffsetDateTime createdAt,
        OffsetDateTime readAt,
        String clientId) {

    /** Le même message, vu par un autre utilisateur (pour le temps réel). */
    public MessageItem forViewer(long viewerUserId) {
        boolean mine = senderUserId != null && senderUserId == viewerUserId;
        return new MessageItem(id, conversationId, senderUserId, senderRole, senderName, mine, text,
                createdAt, readAt, clientId);
    }
}
