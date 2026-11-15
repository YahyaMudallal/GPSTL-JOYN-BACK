package com.gpstl.joyn.application.event;

/**
 * Publié par ApplicationService quand un recruteur (ou un admin) change le statut d'une candidature.
 * La messagerie (CO26) l'écoute pour ouvrir automatiquement une conversation
 * quand la candidature passe en entretien ou est acceptée.
 *
 * @param statusCode le nouveau statut (PENDING, VIEWED, IN_REVIEW, INTERVIEW, ACCEPTED, REJECTED, WITHDRAWN)
 */
public record ApplicationStatusChanged(Long applicationId, String statusCode) {
}
