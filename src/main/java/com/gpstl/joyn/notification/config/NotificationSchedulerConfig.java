package com.gpstl.joyn.notification.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration pour activer la planification Spring des tâches d'arrière-plan du module notifications.
 */
@Configuration
@EnableScheduling
public class NotificationSchedulerConfig {
}
