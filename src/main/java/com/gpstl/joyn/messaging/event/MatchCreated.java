package com.gpstl.joyn.messaging.event;

/**
 * À publier par le futur module de matching (swipe), juste après avoir créé un match :
 *
 *     events.publishEvent(new MatchCreated(match.getId()));
 *
 * La messagerie l'écoute et ouvre automatiquement la conversation du match
 * (voir AutomaticConversationListener). Aucun autre changement n'est nécessaire.
 */
public record MatchCreated(Long matchId) {
}
