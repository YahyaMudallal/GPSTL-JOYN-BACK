package com.gpstl.joyn.candidate.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Réponse de GET /api/v1/candidates/dashboard (CA36) : les chiffres du candidat connecté.
 * Chaque bloc correspond à une carte de la maquette candidate_dashboard.png.
 *
 * Suivent la période demandée (7d, 30d, all) : applications.total / by_status, swipes,
 * matches, conversations.total, recent_matches et recent_activities.
 * Décrivent la situation d'aujourd'hui (la période ne les change pas) : pipeline,
 * applications.active, conversations.unread_messages, interviews et new_offers_count.
 */
public record CandidateDashboardResponse(
        String period,
        @JsonProperty("generated_at") OffsetDateTime generatedAt,
        Pipeline pipeline,
        Applications applications,
        Swipes swipes,
        Matches matches,
        @JsonProperty("recent_matches") List<RecentMatch> recentMatches,
        Conversations conversations,
        Interviews interviews,
        @JsonProperty("new_offers_count") long newOffersCount,
        @JsonProperty("recent_activities") List<Activity> recentActivities) {

    /**
     * Carte "Candidatures en cours" de la maquette (ex. 14 actives : Intéressé 5, Envoyée 4, Vue 3, Entretien 2).
     *
     * @param interested offres likées encore publiées, où le candidat n'a pas encore postulé
     * @param sent       candidatures PENDING
     * @param viewed     candidatures VIEWED ou IN_REVIEW
     * @param interview  candidatures INTERVIEW
     * @param total      somme des quatre
     */
    public record Pipeline(long total, long interested, long sent, long viewed, long interview) {
    }

    /**
     * @param total    candidatures envoyées pendant la période
     * @param active   candidatures encore en cours aujourd'hui (PENDING, VIEWED, IN_REVIEW, INTERVIEW)
     * @param byStatus nombre de candidatures de la période par statut (tous les statuts, même à 0)
     */
    public record Applications(
            long total,
            long active,
            @JsonProperty("by_status") Map<String, Long> byStatus) {
    }

    /** Swipes émis par le candidat sur des offres : LIKE = "Intéressé", PASS = offre ignorée. */
    public record Swipes(long total, long likes, long passes) {
    }

    /** @param active matchs encore ouverts (statut ACTIVE et pas expirés) */
    public record Matches(long total, long active) {
    }

    /**
     * Une ligne de la carte "Nouveaux Matchs".
     *
     * @param compatibilityScore % de compatibilité (0 à 100), ou null s'il n'a pas été calculé
     * @param conversationId     conversation liée au match, ou null s'il n'y en a pas encore
     */
    public record RecentMatch(
            @JsonProperty("match_id") long matchId,
            @JsonProperty("job_offer_id") long jobOfferId,
            @JsonProperty("job_title") String jobTitle,
            @JsonProperty("company_name") String companyName,
            @JsonProperty("company_logo_url") String companyLogoUrl,
            @JsonProperty("compatibility_score") Integer compatibilityScore,
            String status,
            @JsonProperty("conversation_id") Long conversationId,
            @JsonProperty("created_at") OffsetDateTime createdAt) {
    }

    /**
     * @param total          conversations ouvertes pendant la période
     * @param unreadMessages messages reçus et pas encore lus (toutes périodes confondues)
     */
    public record Conversations(
            long total,
            @JsonProperty("unread_messages") long unreadMessages) {
    }

    /**
     * @param upcoming nombre d'entretiens à venir (proposés ou confirmés)
     * @param next     le prochain entretien, ou null s'il n'y en a pas
     */
    public record Interviews(long upcoming, NextInterview next) {
    }

    /**
     * Carte "Entretien à venir".
     * job_offer_id sert au bouton "Consulter l'offre", conversation_id au bouton "Ouvrir la discussion"
     * (null si aucune conversation n'est encore ouverte).
     */
    public record NextInterview(
            long id,
            @JsonProperty("scheduled_at") OffsetDateTime scheduledAt,
            @JsonProperty("duration_minutes") int durationMinutes,
            String mode,
            String status,
            @JsonProperty("location_or_link") String locationOrLink,
            @JsonProperty("job_offer_id") long jobOfferId,
            @JsonProperty("job_title") String jobTitle,
            @JsonProperty("company_name") String companyName,
            @JsonProperty("conversation_id") Long conversationId) {
    }

    /**
     * Une ligne du fil "activité récente".
     *
     * @param type APPLICATION_SENT, APPLICATION_VIEWED, OFFER_LIKED, MATCH, MESSAGE_RECEIVED ou INTERVIEW_PLANNED
     */
    public record Activity(
            String type,
            @JsonProperty("occurred_at") OffsetDateTime occurredAt,
            @JsonProperty("job_offer_id") long jobOfferId,
            @JsonProperty("job_title") String jobTitle,
            @JsonProperty("company_name") String companyName) {
    }
}
