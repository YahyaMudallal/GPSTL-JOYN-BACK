package com.gpstl.joyn.messaging.service;

import com.gpstl.joyn.application.event.ApplicationStatusChanged;
import com.gpstl.joyn.messaging.event.MatchCreated;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Ouverture AUTOMATIQUE des conversations (CO26) :
 *   - quand un recruteur passe une candidature en entretien (INTERVIEW) ou l'accepte (ACCEPTED) ;
 *   - quand un match est créé (événement MatchCreated, à publier par le futur module de matching).
 *
 * @EventListener s'exécute dans la transaction de celui qui publie l'événement : par exemple, le statut
 * de la candidature et la conversation sont enregistrés ensemble, ou pas du tout.
 * @Transactional crée une transaction seulement s'il n'y en a pas déjà une.
 */
@Component
public class AutomaticConversationListener {

    static final Set<String> OPENING_STATUSES = Set.of("INTERVIEW", "ACCEPTED");

    private final MessagingService messagingService;

    public AutomaticConversationListener(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    @EventListener
    @Transactional
    public void onApplicationStatusChanged(ApplicationStatusChanged event) {
        if (event.applicationId() != null && OPENING_STATUSES.contains(event.statusCode())) {
            messagingService.ensureConversationForApplication(event.applicationId());
        }
    }

    @EventListener
    @Transactional
    public void onMatchCreated(MatchCreated event) {
        if (event.matchId() != null) {
            messagingService.ensureConversationForMatch(event.matchId());
        }
    }
}
