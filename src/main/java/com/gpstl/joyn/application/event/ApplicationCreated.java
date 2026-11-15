package com.gpstl.joyn.application.event;

/**
 * Publié par ApplicationService lorsqu'un candidat postule à une offre d'emploi.
 * Utilisé par le service de notifications pour avertir le recruteur responsable de l'offre.
 *
 * @param applicationId   identifiant de la candidature créée
 * @param jobOfferId      identifiant de l'offre d'emploi
 * @param candidateUserId identifiant utilisateur (users.id) du candidat qui postule
 * @param recruiterUserId identifiant utilisateur (users.id) du recruteur responsable de l'offre (peut être null)
 */
public record ApplicationCreated(
        Long applicationId,
        Long jobOfferId,
        Long candidateUserId,
        Long recruiterUserId) {
}
