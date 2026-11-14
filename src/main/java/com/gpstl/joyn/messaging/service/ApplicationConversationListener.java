package com.gpstl.joyn.messaging.service;

import com.gpstl.joyn.application.event.ApplicationStatusChanged;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Ouverture AUTOMATIQUE d'une conversation (CO26) : quand un recruteur passe une candidature
 * en entretien (INTERVIEW) ou l'accepte (ACCEPTED), la conversation est créée tout de suite.
 *
 * @EventListener s'exécute dans la même transaction que le changement de statut :
 * le statut et la conversation sont enregistrés ensemble, ou pas du tout.
 *
 * Plus tard, quand les matchs seront codés, le même principe pourra ouvrir une conversation
 * à la création d'un match (INSERT INTO conversations (match_id, ...)).
 */
@Component
public class ApplicationConversationListener {

    static final Set<String> OPENING_STATUSES = Set.of("INTERVIEW", "ACCEPTED");

    private final MessagingService messagingService;

    public ApplicationConversationListener(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    @EventListener
    public void onApplicationStatusChanged(ApplicationStatusChanged event) {
        if (event.applicationId() != null && OPENING_STATUSES.contains(event.statusCode())) {
            messagingService.ensureConversationForApplication(event.applicationId());
        }
    }
}
