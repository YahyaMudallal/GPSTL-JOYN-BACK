package com.gpstl.joyn.messaging.event;

import com.gpstl.joyn.messaging.dto.MessageItem;

import java.util.Set;

/**
 * Événement interne de la messagerie, envoyé en temps réel (SSE) aux participants
 * APRÈS l'enregistrement en base (voir MessagingStreamService).
 *
 * @param type           "message" (nouveau message) ou "read" (messages lus)
 * @param conversationId la conversation concernée
 * @param recipients     users.id des participants à prévenir
 * @param message        le message (type "message"), null pour "read"
 * @param readerUserId   qui a lu (type "read"), null pour "message"
 */
public record MessagingEvent(String type, long conversationId, Set<Long> recipients,
                             MessageItem message, Long readerUserId) {

    public static MessagingEvent newMessage(MessageItem message, Set<Long> recipients) {
        return new MessagingEvent("message", message.conversationId(), recipients, message, null);
    }

    public static MessagingEvent read(long conversationId, long readerUserId, Set<Long> recipients) {
        return new MessagingEvent("read", conversationId, recipients, null, readerUserId);
    }
}
