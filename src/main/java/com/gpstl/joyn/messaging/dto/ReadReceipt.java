package com.gpstl.joyn.messaging.dto;

/**
 * Réponse de PATCH /api/v1/conversations/{id}/read.
 *
 * @param markedAsRead nombre de messages qui viennent d'être marqués comme lus
 * @param unread       messages encore non lus dans cette conversation (0 après l'appel)
 */
public record ReadReceipt(long conversationId, long markedAsRead, long unread) {
}
