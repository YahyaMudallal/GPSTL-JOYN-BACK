package com.gpstl.joyn.messaging.service;

import com.gpstl.joyn.messaging.event.MessagingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Temps réel de la messagerie avec SSE (Server-Sent Events), inclus dans Spring : aucun service externe.
 *
 * Le front ouvre GET /api/v1/conversations/stream et garde la connexion ouverte. À chaque nouveau
 * message (ou lecture), le back lui pousse un événement, sans que le front ait besoin de redemander.
 *
 *   event: message   data: {le message, comme dans GET /messages}
 *   event: read      data: {"conversationId": 12, "readerUserId": 7}
 *
 * Les connexions sont gardées en mémoire (une liste par utilisateur, plusieurs onglets possibles).
 * Limite connue : avec plusieurs instances du back, il faudrait un broker partagé (Redis, ...).
 * Pour le projet (une seule instance), la mémoire suffit. Sans SSE, le front peut aussi simplement
 * recharger GET /messages de temps en temps : les routes REST restent la source de vérité.
 */
@Service
public class MessagingStreamService {

    private static final Logger log = LoggerFactory.getLogger(MessagingStreamService.class);
    static final Duration TIMEOUT = Duration.ofMinutes(30);

    private final Map<Long, List<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    /** Ouvre un flux pour cet utilisateur et l'enregistre jusqu'à sa fermeture. */
    public SseEmitter subscribe(long userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT.toMillis());
        List<SseEmitter> list = emittersByUser.computeIfAbsent(userId, id -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        Runnable remove = () -> removeEmitter(userId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(error -> remove.run());

        // Premier événement : confirme au front que le flux est ouvert.
        send(userId, emitter, "connected", Map.of("userId", userId));
        return emitter;
    }

    /** Appelé après le commit de la transaction : le message est bien en base avant d'être annoncé. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMessagingEvent(MessagingEvent event) {
        for (Long userId : event.recipients()) {
            Object payload = "message".equals(event.type())
                    ? event.message().forViewer(userId)
                    : Map.of("conversationId", event.conversationId(), "readerUserId", event.readerUserId());
            publish(userId, event.type(), payload);
        }
    }

    /** Envoie un événement à toutes les connexions ouvertes d'un utilisateur. */
    public void publish(long userId, String eventName, Object payload) {
        List<SseEmitter> list = emittersByUser.get(userId);
        if (list == null) {
            return; // pas connecté en temps réel : il verra le message au prochain chargement
        }
        for (SseEmitter emitter : list) {
            send(userId, emitter, eventName, payload);
        }
    }

    /** Nombre de connexions ouvertes pour un utilisateur (utile pour les tests). */
    public int connectionCount(long userId) {
        List<SseEmitter> list = emittersByUser.get(userId);
        return list == null ? 0 : list.size();
    }

    private void send(long userId, SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ex) {
            // Connexion fermée côté client (onglet fermé, réseau coupé) : on l'oublie.
            log.debug("SSE connection closed for user {}", userId);
            removeEmitter(userId, emitter);
        }
    }

    private void removeEmitter(long userId, SseEmitter emitter) {
        emittersByUser.computeIfPresent(userId, (id, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }
}
