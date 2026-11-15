package com.gpstl.joyn.notification.controller;

import com.gpstl.joyn.notification.dto.NotificationCountResponse;
import com.gpstl.joyn.notification.dto.NotificationItem;
import com.gpstl.joyn.notification.dto.NotificationListResponse;
import com.gpstl.joyn.notification.exception.NotificationException;
import com.gpstl.joyn.notification.model.NotificationType;
import com.gpstl.joyn.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NotificationControllerTest {

    private NotificationService notificationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new NotificationController(notificationService))
                .setControllerAdvice(new NotificationExceptionHandler())
                .setCustomArgumentResolvers(new TestJwtArgumentResolver())
                .build();
    }

    private static class TestJwtArgumentResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterAnnotation(AuthenticationPrincipal.class) != null
                    && parameter.getParameterType().equals(Jwt.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            String sub = webRequest.getHeader("X-Test-Sub");
            if (sub != null) {
                return Jwt.withTokenValue("test-token")
                        .header("alg", "none")
                        .subject(sub)
                        .build();
            }
            return null;
        }
    }

    @Test
    void getNotifications_returnsListAndUnreadCount() throws Exception {
        NotificationItem item = new NotificationItem(
                1L, NotificationType.APPLICATION_UPDATE, "Mise à jour", "Message test",
                "/applications/1", false, null, OffsetDateTime.now());

        NotificationListResponse response = new NotificationListResponse(
                List.of(item), 1L, 1L, 1, 20, 1);

        when(notificationService.getNotifications(eq(42L), eq(1), eq(20), eq(false), eq(false)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/notifications")
                        .header("X-Test-Sub", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications[0].id").value(1))
                .andExpect(jsonPath("$.notifications[0].type").value("APPLICATION_UPDATE"))
                .andExpect(jsonPath("$.notifications[0].title").value("Mise à jour"))
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.page").value(1));
    }

    @Test
    void getNotifications_withFiltersAndPurge() throws Exception {
        NotificationListResponse empty = new NotificationListResponse(List.of(), 0L, 0L, 2, 10, 0);

        when(notificationService.getNotifications(eq(42L), eq(2), eq(10), eq(true), eq(true)))
                .thenReturn(empty);

        mockMvc.perform(get("/api/v1/notifications")
                        .header("X-Test-Sub", "42")
                        .param("page", "2")
                        .param("limit", "10")
                        .param("unreadOnly", "true")
                        .param("autoDeleteConsulted", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.pageSize").value(10));

        verify(notificationService).getNotifications(42L, 2, 10, true, true);
    }

    @Test
    void getUnreadCount_returnsCount() throws Exception {
        when(notificationService.getUnreadCount(42L)).thenReturn(new NotificationCountResponse(3L));

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("X-Test-Sub", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(3));
    }

    @Test
    void markAsRead_marksAndReturnsUpdated() throws Exception {
        NotificationItem readItem = new NotificationItem(
                10L, NotificationType.NEW_MESSAGE, "Nouveau message", "Hello",
                "/conversations/1", true, OffsetDateTime.now(), OffsetDateTime.now().minusMinutes(5));

        when(notificationService.markAsRead(42L, 10L, false)).thenReturn(readItem);

        mockMvc.perform(patch("/api/v1/notifications/10/read")
                        .header("X-Test-Sub", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.isRead").value(true));
    }

    @Test
    void markAsRead_withDeleteParam_callsServiceWithDeleteTrue() throws Exception {
        NotificationItem readItem = new NotificationItem(
                10L, NotificationType.NEW_MESSAGE, "Nouveau message", "Hello",
                "/conversations/1", true, OffsetDateTime.now(), OffsetDateTime.now().minusMinutes(5));

        when(notificationService.markAsRead(42L, 10L, true)).thenReturn(readItem);

        mockMvc.perform(patch("/api/v1/notifications/10/read")
                        .header("X-Test-Sub", "42")
                        .param("delete", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));

        verify(notificationService).markAsRead(42L, 10L, true);
    }

    @Test
    void markAllAsRead_returnsActionResponse() throws Exception {
        when(notificationService.markAllAsRead(42L)).thenReturn(5);

        mockMvc.perform(patch("/api/v1/notifications/read-all")
                        .header("X-Test-Sub", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affectedCount").value(5))
                .andExpect(jsonPath("$.message").value("Toutes les notifications ont été marquées comme lues"));
    }

    @Test
    void deleteNotification_deletesSpecific() throws Exception {
        doNothing().when(notificationService).deleteNotification(42L, 7L);

        mockMvc.perform(delete("/api/v1/notifications/7")
                        .header("X-Test-Sub", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affectedCount").value(1))
                .andExpect(jsonPath("$.message").value("Notification supprimée avec succès"));

        verify(notificationService).deleteNotification(42L, 7L);
    }

    @Test
    void deleteReadNotifications_purgesRead() throws Exception {
        when(notificationService.deleteReadNotifications(42L)).thenReturn(4);

        mockMvc.perform(delete("/api/v1/notifications/read")
                        .header("X-Test-Sub", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affectedCount").value(4))
                .andExpect(jsonPath("$.message").value("Notifications lues supprimées avec succès"));
    }

    @Test
    void deleteAllNotifications_clearsAll() throws Exception {
        when(notificationService.deleteAllNotifications(42L)).thenReturn(12);

        mockMvc.perform(delete("/api/v1/notifications")
                        .header("X-Test-Sub", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affectedCount").value(12))
                .andExpect(jsonPath("$.message").value("Toutes les notifications ont été supprimées avec succès"));
    }

    @Test
    void unauthenticatedRequest_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    @Test
    void invalidSubject_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                        .header("X-Test-Sub", "not-a-number"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid authenticated user identity"));
    }

    @Test
    void invalidIdParam_returns400() throws Exception {
        mockMvc.perform(delete("/api/v1/notifications/-5")
                        .header("X-Test-Sub", "42"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("id must be a positive integer"));
    }

    @Test
    void notFoundNotification_returns404() throws Exception {
        doThrow(NotificationException.notFound("notificationId", "Notification not found"))
                .when(notificationService).deleteNotification(42L, 999L);

        mockMvc.perform(delete("/api/v1/notifications/999")
                        .header("X-Test-Sub", "42"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Notification not found"));
    }
}
