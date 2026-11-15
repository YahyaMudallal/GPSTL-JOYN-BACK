package com.gpstl.joyn.messaging.dto;

import java.time.OffsetDateTime;

/**
 * Une conversation, vue par l'utilisateur connecté.
 * Les noms sont en camelCase, comme ceux que lit déjà le front Flutter
 * (recruiter_workspace : id, name, candidateId, offerTitle, lastMessage, unread).
 *
 * @param name          le nom à afficher : pour un recruteur, le candidat ; pour un candidat, l'entreprise
 * @param applicationId candidature à l'origine de la conversation (null si elle vient d'un match)
 * @param matchId       match à l'origine de la conversation (null si elle vient d'une candidature)
 * @param lastMessage   texte du dernier message, ou null s'il n'y en a pas encore
 * @param unread        messages reçus (envoyés par quelqu'un d'autre) et pas encore lus
 */
public record ConversationItem(
        long id,
        String name,
        Long applicationId,
        Long matchId,
        long jobOfferId,
        String offerTitle,
        long companyId,
        String companyName,
        String companyLogoUrl,
        long candidateId,
        String candidateName,
        String candidatePhotoUrl,
        String lastMessage,
        OffsetDateTime lastMessageAt,
        long unread,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
