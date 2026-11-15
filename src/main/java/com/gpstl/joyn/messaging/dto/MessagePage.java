package com.gpstl.joyn.messaging.dto;

import java.util.List;

/**
 * Une page de messages (pagination par curseur).
 *
 * La page contient les messages les plus récents AVANT le curseur demandé.
 * Pour charger les messages plus anciens, rappeler la route avec ?before=nextCursor.
 *
 * @param data       les messages de la page (ordre choisi par ?order=asc|desc, asc par défaut)
 * @param nextCursor id à passer dans ?before= pour la page suivante (plus ancienne), null s'il n'y en a plus
 * @param hasMore    true s'il reste des messages plus anciens
 */
public record MessagePage(List<MessageItem> data, Long nextCursor, boolean hasMore) {
}
