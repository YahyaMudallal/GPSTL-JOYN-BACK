package com.gpstl.joyn.notification.service;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class NotificationCleanupSchedulerTest {

    @Test
    void runCleanup_callsNotificationServiceCleanup() {
        NotificationService service = mock(NotificationService.class);
        when(service.cleanupOldNotifications()).thenReturn(8);

        NotificationCleanupScheduler scheduler = new NotificationCleanupScheduler(service);
        scheduler.runCleanup();

        verify(service).cleanupOldNotifications();
    }

    @Test
    void runCleanup_handlesExceptionWithoutThrowing() {
        NotificationService service = mock(NotificationService.class);
        doThrow(new RuntimeException("Database error")).when(service).cleanupOldNotifications();

        NotificationCleanupScheduler scheduler = new NotificationCleanupScheduler(service);
        // Ne doit pas propager l'exception pour ne pas faire crasher l'exécuteur de tâches
        scheduler.runCleanup();

        verify(service).cleanupOldNotifications();
    }
}
