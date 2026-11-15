package com.gpstl.joyn.notification.repository;

import com.gpstl.joyn.notification.model.Notification;
import com.gpstl.joyn.notification.model.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Recherche paginée de toutes les notifications d'un utilisateur, triées par date décroissante.
     */
    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * Recherche paginée des notifications non lues d'un utilisateur, triées par date décroissante.
     */
    Page<Notification> findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * Compte le nombre de notifications non lues pour un utilisateur donné.
     */
    long countByUserIdAndReadAtIsNull(Long userId);

    /**
     * Compte total des notifications d'un utilisateur.
     */
    long countByUserId(Long userId);

    /**
     * Recherche une notification par son identifiant et l'utilisateur propriétaire.
     */
    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    /**
     * Recherche la notification non lue la plus récente pour un utilisateur, un type et un lien donnés.
     * Permet le regroupement intelligent (évite le flood en agrégeant les notifications similaires non lues).
     */
    Optional<Notification> findFirstByUserIdAndTypeAndLinkAndReadAtIsNullOrderByCreatedAtDesc(
            Long userId, NotificationType type, String link);

    /**
     * Marque toutes les notifications non lues d'un utilisateur comme lues.
     */
    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.user.id = :userId AND n.readAt IS NULL")
    int markAllAsRead(@Param("userId") Long userId, @Param("now") OffsetDateTime now);

    /**
     * Supprime toutes les notifications lues d'un utilisateur (purge des notifications consultées).
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.user.id = :userId AND n.readAt IS NOT NULL")
    int deleteReadByUserId(@Param("userId") Long userId);

    /**
     * Supprime toutes les notifications d'un utilisateur.
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.user.id = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);

    /**
     * Nettoyage automatique : supprime les notifications lues antérieures à une date seuil.
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.readAt IS NOT NULL AND n.readAt < :threshold")
    int deleteReadOlderThan(@Param("threshold") OffsetDateTime threshold);

    /**
     * Nettoyage automatique : supprime toutes les notifications (même non lues) antérieures à une date seuil.
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.createdAt < :threshold")
    int deleteOlderThan(@Param("threshold") OffsetDateTime threshold);

    /**
     * Récupère les identifiants des notifications les plus anciennes dépassant la limite autorisée par utilisateur.
     */
    @Query(value = """
            SELECT id FROM notifications
            WHERE user_id = :userId
            ORDER BY created_at DESC
            OFFSET :maxAllowed
            """, nativeQuery = true)
    List<Long> findExcessNotificationIds(@Param("userId") Long userId, @Param("maxAllowed") int maxAllowed);

    /**
     * Supprime une liste de notifications par leurs identifiants.
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.id IN :ids")
    int deleteByIds(@Param("ids") List<Long> ids);
}
