package com.gpstl.joyn.common.dto;

import com.gpstl.joyn.common.exception.DashboardException;

import java.time.Duration;
import java.time.Instant;

/**
 * Période d'un tableau de bord : paramètre ?period=7d, 30d ou all.
 * Partagé par le dashboard candidat (CA36) et le dashboard recruteur (R49).
 */
public enum DashboardPeriod {

    SEVEN_DAYS("7d", 7),
    THIRTY_DAYS("30d", 30),
    ALL("all", 0);

    private final String code;
    private final int days;

    DashboardPeriod(String code, int days) {
        this.code = code;
        this.days = days;
    }

    /** Valeur renvoyée dans le JSON ("7d", "30d" ou "all"). */
    public String code() {
        return code;
    }

    /** Nombre de jours de la période (0 pour "all"). */
    public int days() {
        return days;
    }

    public boolean isAll() {
        return this == ALL;
    }

    /**
     * Début de la période. Pour "all" on renvoie le 1er janvier 1970 au lieu de null :
     * les requêtes SQL restent simples (created_at >= :from) sans cas particulier.
     */
    public Instant start(Instant now) {
        return isAll() ? Instant.EPOCH : now.minus(Duration.ofDays(days));
    }

    /** Lit le paramètre de la requête ; valeur vide = défaut ; valeur inconnue = 400 sur le champ "period". */
    public static DashboardPeriod parse(String raw, DashboardPeriod defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
        for (DashboardPeriod period : values()) {
            if (period.code.equals(value)) {
                return period;
            }
        }
        throw DashboardException.badRequest("period", "Valeurs acceptées : 7d, 30d, all");
    }
}
