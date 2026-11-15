package com.gpstl.joyn.notification.service;

import com.gpstl.joyn.common.model.User;
import com.gpstl.joyn.notification.dto.NotificationCountResponse;
import com.gpstl.joyn.notification.dto.NotificationItem;
import com.gpstl.joyn.notification.dto.NotificationListResponse;
import com.gpstl.joyn.notification.exception.NotificationException;
import com.gpstl.joyn.notification.model.Notification;
import com.gpstl.joyn.notification.model.NotificationType;
import com.gpstl.joyn.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    private NotificationRepository repository;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        service = new NotificationService(repository, 100, 7, 30);
    }

    @Test
    void getNotifications_returnsPaginatedListWithCounts() {
        Long userId = 1L;
        User user = mock(User.class);
        Notification n1 = new Notification(user, NotificationType.APPLICATION_UPDATE, "Titre 1", "Msg 1", "/app/1");
        Notification n2 = new Notification(user, NotificationType.NEW_MESSAGE, "Titre 2", "Msg 2", "/conv/2");

        when(repository.findByUserIdOrderByCreatedAtDesc(eq(userId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(n1, n2), PageRequest.of(0, 20), 2));
        when(repository.countByUserIdAndReadAtIsNull(userId)).thenReturn(1L);
        when(repository.countByUserId(userId)).thenReturn(2L);

        NotificationListResponse response = service.getNotifications(userId, 1, 20, false, false);

        assertThat(response.notifications()).hasSize(2);
        assertThat(response.unreadCount()).isEqualTo(1L);
        assertThat(response.totalCount()).isEqualTo(2L);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(20);
        assertThat(response.totalPages()).isEqualTo(1);
        verify(repository, never()).deleteReadByUserId(any());
    }

    @Test
    void getNotifications_withAutoDeleteConsulted_deletesReadFirst() {
        Long userId = 1L;
        when(repository.findByUserIdOrderByCreatedAtDesc(eq(userId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.getNotifications(userId, 1, 20, false, true);

        verify(repository).deleteReadByUserId(userId);
    }

    @Test
    void getNotifications_withUnreadOnly_queriesUnread() {
        Long userId = 1L;
        when(repository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(eq(userId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.getNotifications(userId, 1, 20, true, false);

        verify(repository).findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(eq(userId), any(PageRequest.class));
        verify(repository, never()).findByUserIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void getNotifications_invalidPage_throwsBadRequest() {
        assertThatThrownBy(() -> service.getNotifications(1L, 0, 20, false, false))
                .isInstanceOf(NotificationException.class)
                .hasMessageContaining("page must be greater than or equal to 1");
    }

    @Test
    void getNotifications_invalidLimit_throwsBadRequest() {
        assertThatThrownBy(() -> service.getNotifications(1L, 1, 55, false, false))
                .isInstanceOf(NotificationException.class)
                .hasMessageContaining("limit must be between 1 and 50");
    }

    @Test
    void getUnreadCount_returnsCount() {
        when(repository.countByUserIdAndReadAtIsNull(1L)).thenReturn(4L);

        NotificationCountResponse response = service.getUnreadCount(1L);

        assertThat(response.unreadCount()).isEqualTo(4L);
    }

    @Test
    void markAsRead_marksNotificationAsRead() {
        Long userId = 1L;
        Long notifId = 10L;
        User user = mock(User.class);
        Notification notif = new Notification(user, NotificationType.APPLICATION_UPDATE, "Titre", "Msg", "/app/1");

        when(repository.findByIdAndUserId(notifId, userId)).thenReturn(Optional.of(notif));
        when(repository.saveAndFlush(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationItem item = service.markAsRead(userId, notifId, false);

        assertThat(item.isRead()).isTrue();
        assertThat(item.readAt()).isNotNull();
        verify(repository).saveAndFlush(notif);
        verify(repository, never()).delete(any());
    }

    @Test
    void markAsRead_withDeleteImmediately_deletesFromRepository() {
        Long userId = 1L;
        Long notifId = 10L;
        User user = mock(User.class);
        Notification notif = new Notification(user, NotificationType.APPLICATION_UPDATE, "Titre", "Msg", "/app/1");

        when(repository.findByIdAndUserId(notifId, userId)).thenReturn(Optional.of(notif));

        NotificationItem item = service.markAsRead(userId, notifId, true);

        assertThat(item.isRead()).isTrue();
        verify(repository).delete(notif);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void markAsRead_notFound_throwsException() {
        when(repository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsRead(1L, 99L, false))
                .isInstanceOf(NotificationException.class)
                .hasMessageContaining("Notification not found");
    }

    @Test
    void markAllAsRead_callsRepository() {
        when(repository.markAllAsRead(eq(1L), any(OffsetDateTime.class))).thenReturn(5);

        int affected = service.markAllAsRead(1L);

        assertThat(affected).isEqualTo(5);
        verify(repository).markAllAsRead(eq(1L), any(OffsetDateTime.class));
    }

    @Test
    void deleteNotification_deletesExisting() {
        User user = mock(User.class);
        Notification notif = new Notification(user, NotificationType.SYSTEM, "T", "M", "/");
        when(repository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(notif));

        service.deleteNotification(1L, 10L);

        verify(repository).delete(notif);
    }

    @Test
    void deleteReadNotifications_callsRepository() {
        when(repository.deleteReadByUserId(1L)).thenReturn(3);

        int count = service.deleteReadNotifications(1L);

        assertThat(count).isEqualTo(3);
        verify(repository).deleteReadByUserId(1L);
    }

    @Test
    void deleteAllNotifications_callsRepository() {
        when(repository.deleteAllByUserId(1L)).thenReturn(7);

        int count = service.deleteAllNotifications(1L);

        assertThat(count).isEqualTo(7);
        verify(repository).deleteAllByUserId(1L);
    }

    @Test
    void createNotification_savesAndPrunesExcess() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(repository.saveAndFlush(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findExcessNotificationIds(1L, 100)).thenReturn(List.of(101L, 102L));

        Notification created = service.createNotification(user, NotificationType.SYSTEM, "Test", "Msg", "/test");

        assertThat(created.getTitle()).isEqualTo("Test");
        verify(repository).saveAndFlush(any(Notification.class));
        verify(repository).deleteByIds(List.of(101L, 102L));
    }

    @Test
    void notifyRecruiterApplication_noRecentUnread_createsNewNotification() {
        User recruiter = mock(User.class);
        when(recruiter.getId()).thenReturn(2L);
        when(repository.findFirstByUserIdAndTypeAndLinkAndReadAtIsNullOrderByCreatedAtDesc(
                eq(2L), eq(NotificationType.NEW_APPLICATION), eq("/recruiters/jobs/5/recommendations")))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        Notification notif = service.notifyRecruiterApplication(recruiter, "Alice Dupont", "Dev Java", 5L);

        assertThat(notif.getType()).isEqualTo(NotificationType.NEW_APPLICATION);
        assertThat(notif.getTitle()).isEqualTo("Nouvelle candidature reçue");
        assertThat(notif.getMessage()).contains("Alice Dupont");
        assertThat(notif.getMessage()).contains("Dev Java");
    }

    @Test
    void notifyRecruiterApplication_recentUnreadExists_groupsIntoExistingNotification() {
        User recruiter = mock(User.class);
        when(recruiter.getId()).thenReturn(2L);
        Notification existing = new Notification(recruiter, NotificationType.NEW_APPLICATION, "Nouvelle candidature reçue",
                "Bob a postulé...", "/recruiters/jobs/5/recommendations");
        existing.setCreatedAt(OffsetDateTime.now().minusMinutes(10));

        when(repository.findFirstByUserIdAndTypeAndLinkAndReadAtIsNullOrderByCreatedAtDesc(
                eq(2L), eq(NotificationType.NEW_APPLICATION), eq("/recruiters/jobs/5/recommendations")))
                .thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        Notification notif = service.notifyRecruiterApplication(recruiter, "Alice Dupont", "Dev Java", 5L);

        assertThat(notif.getTitle()).isEqualTo("Nouvelles candidatures reçues");
        assertThat(notif.getMessage()).contains("Plusieurs candidats ont postulé");
        assertThat(notif.getMessage()).contains("Alice Dupont");
        verify(repository).saveAndFlush(existing);
    }

    @Test
    void notifyNewMessage_noRecentUnread_createsNewNotification() {
        User recipient = mock(User.class);
        when(recipient.getId()).thenReturn(3L);
        when(repository.findFirstByUserIdAndTypeAndLinkAndReadAtIsNullOrderByCreatedAtDesc(
                eq(3L), eq(NotificationType.NEW_MESSAGE), eq("/conversations/12")))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        Notification notif = service.notifyNewMessage(recipient, "Marc", "Bonjour, quand seriez-vous disponible ?", 12L);

        assertThat(notif.getType()).isEqualTo(NotificationType.NEW_MESSAGE);
        assertThat(notif.getTitle()).isEqualTo("Nouveau message de Marc");
        assertThat(notif.getMessage()).contains("Marc :");
        assertThat(notif.getMessage()).contains("Bonjour, quand seriez-vous disponible ?");
    }

    @Test
    void notifyNewMessage_existingUnread_updatesContent() {
        User recipient = mock(User.class);
        when(recipient.getId()).thenReturn(3L);
        Notification existing = new Notification(recipient, NotificationType.NEW_MESSAGE, "Nouveau message de Marc",
                "Marc : Premier message", "/conversations/12");

        when(repository.findFirstByUserIdAndTypeAndLinkAndReadAtIsNullOrderByCreatedAtDesc(
                eq(3L), eq(NotificationType.NEW_MESSAGE), eq("/conversations/12")))
                .thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        Notification notif = service.notifyNewMessage(recipient, "Marc", "Deuxième message", 12L);

        assertThat(notif.getMessage()).contains("Deuxième message");
        verify(repository).saveAndFlush(existing);
    }

    @Test
    void cleanupOldNotifications_callsRepositoryPurge() {
        when(repository.deleteReadOlderThan(any(OffsetDateTime.class))).thenReturn(10);
        when(repository.deleteOlderThan(any(OffsetDateTime.class))).thenReturn(4);

        int total = service.cleanupOldNotifications();

        assertThat(total).isEqualTo(14);
        verify(repository).deleteReadOlderThan(any(OffsetDateTime.class));
        verify(repository).deleteOlderThan(any(OffsetDateTime.class));
    }
}
