package com.gpstl.joyn.notification.service;

import com.gpstl.joyn.common.model.User;
import com.gpstl.joyn.notification.dto.NotificationCountResponse;
import com.gpstl.joyn.notification.dto.NotificationItem;
import com.gpstl.joyn.notification.dto.NotificationListResponse;
import com.gpstl.joyn.notification.exception.NotificationException;
import com.gpstl.joyn.notification.model.Notification;
import com.gpstl.joyn.notification.model.NotificationType;
import com.gpstl.joyn.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Service gérant le cycle de vie des notifications utilisateurs (candidats et recruteurs).
 *
 * Fonctionnalités clés :
 *  - Consultation paginée avec compteurs de non lus ;
 *  - Suppression immédiate ou différée des notifications consultées ;
 *  - Regroupement intelligent (évite le spam pour les recruteurs ou lors d'échanges de messages rapides) ;
 *  - Plafond maximal de notifications par utilisateur pour éviter la saturation ;
 *  - Nettoyage et purge automatique des anciennes notifications.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_PAGE_SIZE = 50;

    private final NotificationRepository notificationRepository;
    private final int maxPerUser;
    private final int readRetentionDays;
    private final int maxRetentionDays;

    public NotificationService(
            NotificationRepository notificationRepository,
            @Value("${joyn.notifications.max-per-user:100}") int maxPerUser,
            @Value("${joyn.notifications.read-retention-days:7}") int readRetentionDays,
            @Value("${joyn.notifications.max-retention-days:30}") int maxRetentionDays) {
        this.notificationRepository = notificationRepository;
        this.maxPerUser = maxPerUser > 0 ? maxPerUser : 100;
        this.readRetentionDays = readRetentionDays > 0 ? readRetentionDays : 7;
        this.maxRetentionDays = maxRetentionDays > 0 ? maxRetentionDays : 30;
    }

    /**
     * Récupère les notifications paginées d'un utilisateur.
     *
     * @param userId               identifiant de l'utilisateur connecté
     * @param page                 numéro de page (base 1)
     * @param limit                taille de page (1 à 50)
     * @param unreadOnly           si true, ne renvoie que les notifications non lues
     * @param autoDeleteConsulted  si true, supprime les notifications lues de l'utilisateur
     * @return la page de notifications avec les compteurs globaux
     */
    @Transactional
    public NotificationListResponse getNotifications(
            Long userId,
            int page,
            int limit,
            boolean unreadOnly,
            boolean autoDeleteConsulted) {

        validatePagination(page, limit);

        if (autoDeleteConsulted) {
            // Suppression des notifications déjà lues/consultées à la demande
            notificationRepository.deleteReadByUserId(userId);
        }

        PageRequest pageRequest = PageRequest.of(page - 1, limit);
        Page<Notification> notificationPage = unreadOnly
                ? notificationRepository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId, pageRequest)
                : notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageRequest);

        long unreadCount = notificationRepository.countByUserIdAndReadAtIsNull(userId);
        long totalCount = notificationRepository.countByUserId(userId);

        List<NotificationItem> items = notificationPage.getContent().stream()
                .map(NotificationItem::from)
                .toList();

        return new NotificationListResponse(
                items,
                unreadCount,
                totalCount,
                page,
                limit,
                notificationPage.getTotalPages());
    }

    /**
     * Renvoie le nombre de notifications non lues d'un utilisateur.
     */
    @Transactional(readOnly = true)
    public NotificationCountResponse getUnreadCount(Long userId) {
        long count = notificationRepository.countByUserIdAndReadAtIsNull(userId);
        return new NotificationCountResponse(count);
    }

    /**
     * Marque une notification comme consultée / lue, ou la supprime directement si deleteImmediately est true.
     */
    @Transactional
    public NotificationItem markAsRead(Long userId, Long notificationId, boolean deleteImmediately) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> NotificationException.notFound("notificationId", "Notification not found"));

        if (deleteImmediately) {
            notificationRepository.delete(notification);
            notification.markAsRead();
            return NotificationItem.from(notification);
        }

        if (!notification.isRead()) {
            notification.markAsRead();
            notification = notificationRepository.saveAndFlush(notification);
        }
        return NotificationItem.from(notification);
    }

    /**
     * Marque toutes les notifications d'un utilisateur comme lues.
     */
    @Transactional
    public int markAllAsRead(Long userId) {
        return notificationRepository.markAllAsRead(userId, OffsetDateTime.now());
    }

    /**
     * Supprime une notification spécifique appartenant à l'utilisateur.
     */
    @Transactional
    public void deleteNotification(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> NotificationException.notFound("notificationId", "Notification not found"));
        notificationRepository.delete(notification);
    }

    /**
     * Supprime toutes les notifications lues d'un utilisateur.
     */
    @Transactional
    public int deleteReadNotifications(Long userId) {
        return notificationRepository.deleteReadByUserId(userId);
    }

    /**
     * Supprime toutes les notifications d'un utilisateur.
     */
    @Transactional
    public int deleteAllNotifications(Long userId) {
        return notificationRepository.deleteAllByUserId(userId);
    }

    /**
     * Crée une notification générique en appliquant le plafond par utilisateur.
     */
    @Transactional
    public Notification createNotification(
            User user,
            NotificationType type,
            String title,
            String message,
            String link) {

        Notification notification = new Notification(user, type, title, message, link);
        Notification saved = notificationRepository.saveAndFlush(notification);

        // Plafonnement du nombre de notifications par utilisateur pour éviter l'engorgement
        pruneExcessNotifications(user.getId());

        return saved;
    }

    /**
     * Notification intelligente pour les recruteurs lorsqu'un candidat postule à une offre.
     * Si une notification non lue existe déjà pour cette même offre reçue récemment (dans les 2 heures),
     * elle est mise à jour et agrégée plutôt que de créer un doublon intempestif.
     */
    @Transactional
    public Notification notifyRecruiterApplication(
            User recruiterUser,
            String candidateName,
            String jobTitle,
            Long jobOfferId) {

        String link = "/recruiters/jobs/" + jobOfferId + "/recommendations";
        var existing = notificationRepository
                .findFirstByUserIdAndTypeAndLinkAndReadAtIsNullOrderByCreatedAtDesc(
                        recruiterUser.getId(),
                        NotificationType.NEW_APPLICATION,
                        link);

        if (existing.isPresent()) {
            Notification notif = existing.get();
            // Si la notification existante a moins de 2 heures, on l'agrège
            if (notif.getCreatedAt() != null && notif.getCreatedAt().isAfter(OffsetDateTime.now().minusHours(2))) {
                notif.updateContent(
                        "Nouvelles candidatures reçues",
                        "Plusieurs candidats ont postulé à votre offre « " + jobTitle + " » (dernier candidat : " + candidateName + ").");
                return notificationRepository.saveAndFlush(notif);
            }
        }

        String title = "Nouvelle candidature reçue";
        String message = candidateName + " a postulé à votre offre « " + jobTitle + " ».";
        return createNotification(recruiterUser, NotificationType.NEW_APPLICATION, title, message, link);
    }

    /**
     * Notification intelligente pour un nouveau message reçu.
     * Si une notification non lue existe déjà pour cette même conversation, elle est mise à jour
     * avec le nouvel extrait pour ne pas inonder l'utilisateur lors d'un échange actif.
     */
    @Transactional
    public Notification notifyNewMessage(
            User recipientUser,
            String senderName,
            String messageContent,
            Long conversationId) {

        String link = "/conversations/" + conversationId;
        String snippet = truncate(messageContent, 120);

        var existing = notificationRepository
                .findFirstByUserIdAndTypeAndLinkAndReadAtIsNullOrderByCreatedAtDesc(
                        recipientUser.getId(),
                        NotificationType.NEW_MESSAGE,
                        link);

        if (existing.isPresent()) {
            Notification notif = existing.get();
            notif.updateContent(
                    "Nouveau message de " + senderName,
                    senderName + " : " + snippet);
            return notificationRepository.saveAndFlush(notif);
        }

        String title = "Nouveau message de " + senderName;
        String message = senderName + " : " + snippet;
        return createNotification(recipientUser, NotificationType.NEW_MESSAGE, title, message, link);
    }

    /**
     * Nettoyage automatique des notifications :
     *  - Supprime les notifications lues plus anciennes que readRetentionDays ;
     *  - Supprime toutes les notifications plus anciennes que maxRetentionDays.
     */
    @Transactional
    public int cleanupOldNotifications() {
        OffsetDateTime readThreshold = OffsetDateTime.now().minusDays(readRetentionDays);
        OffsetDateTime maxThreshold = OffsetDateTime.now().minusDays(maxRetentionDays);

        int deletedRead = notificationRepository.deleteReadOlderThan(readThreshold);
        int deletedExpired = notificationRepository.deleteOlderThan(maxThreshold);

        int total = deletedRead + deletedExpired;
        if (total > 0) {
            log.info("Nettoyage des notifications : {} lues purgées (> {} jours), {} expirées (> {} jours)",
                    deletedRead, readRetentionDays, deletedExpired, maxRetentionDays);
        }
        return total;
    }

    /**
     * Supprime les notifications les plus anciennes dépassant la limite autorisée pour un utilisateur.
     */
    private void pruneExcessNotifications(Long userId) {
        try {
            List<Long> excessIds = notificationRepository.findExcessNotificationIds(userId, maxPerUser);
            if (excessIds != null && !excessIds.isEmpty()) {
                notificationRepository.deleteByIds(excessIds);
                log.debug("Plafonnement des notifications pour user {} : {} notifications anciennes supprimées",
                        userId, excessIds.size());
            }
        } catch (Exception ex) {
            log.warn("Impossible d'élaguer les notifications excédentaires pour user {} : {}", userId, ex.getMessage());
        }
    }

    private static void validatePagination(int page, int limit) {
        if (page < 1) {
            throw NotificationException.badRequest("page", "page must be greater than or equal to 1");
        }
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw NotificationException.badRequest("limit", "limit must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength - 3) + "...";
    }
}
