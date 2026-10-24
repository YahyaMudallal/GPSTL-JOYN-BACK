package com.gpstl.joyn.candidate.event;

/**
 * Publié quand les compétences ou les préférences de recherche d'un candidat changent.
 * Aucun cache de recommandations n'existe encore : le futur module de matching pourra
 * l'écouter avec @TransactionalEventListener (phase AFTER_COMMIT par défaut), afin de
 * n'invalider son cache qu'une fois la modification réellement validée en base.
 */
public record CandidateMatchingCriteriaChanged(Long candidateId) {
}
