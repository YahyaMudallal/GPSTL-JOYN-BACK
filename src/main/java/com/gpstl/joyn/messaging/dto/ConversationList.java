package com.gpstl.joyn.messaging.dto;

import java.util.List;

/**
 * Les conversations de l'utilisateur connecté, la plus récente d'abord.
 *
 * @param data        les conversations
 * @param totalUnread total des messages non lus, toutes conversations confondues (pour un badge)
 */
public record ConversationList(List<ConversationItem> data, long totalUnread) {
}
