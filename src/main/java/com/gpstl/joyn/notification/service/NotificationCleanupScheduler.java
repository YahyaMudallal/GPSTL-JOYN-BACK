package com.gpstl.joyn.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tâche planifiée pour l'élagage et la suppression automatique des notifications obsolètes.
 *
 * Évite que les notifications ne s'accumulent indéfiniment en base de données.
 * Activé par défaut et configurable via `joyn.notifications.cleanup-cron`.
 */
@Component
@ConditionalOnProperty(name = "joyn.notifications.cleanup-enabled", havingValue = "true", matchIfMissing = true)
public class NotificationCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationCleanupScheduler.class);

    private final NotificationService notificationService;

    public NotificationCleanupScheduler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Exécution périodique du nettoyage (par défaut chaque nuit à 03:00).
     */
    @Scheduled(cron = "${joyn.notifications.cleanup-cron:0 0 3 * * *}")
    public void runCleanup() {
        log.info("Lancement de la tâche planifiée de nettoyage des anciennes notifications...");
        try {
            int cleaned = notificationService.cleanupOldNotifications();
            log.info("Nettoyage des notifications terminé : {} notification(s) supprimée(s).", cleaned);
        } catch (Exception ex) {
            log.error("Erreur lors de l'exécution de la purge planifiée des notifications", ex);
        }
    }
}
