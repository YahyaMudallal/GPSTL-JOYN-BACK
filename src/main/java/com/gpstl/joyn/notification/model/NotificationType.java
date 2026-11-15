package com.gpstl.joyn.notification.model;

/**
 * Types de notifications gérés dans l'application Joyn.
 * Correspond aux valeurs autorisées par la contrainte CHECK de la table SQL `notifications`.
 */
public enum NotificationType {
    NEW_MATCH,
    NEW_MESSAGE,
    APPLICATION_UPDATE,
    NEW_APPLICATION,
    INTERVIEW,
    NEW_OFFER,
    RECOMMENDATION,
    SYSTEM
}
