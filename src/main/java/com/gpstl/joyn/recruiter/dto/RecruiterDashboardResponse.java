package com.gpstl.joyn.recruiter.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Réponse de GET /api/v1/recruiters/dashboard (R49).
 *
 * Les noms des champs sont en camelCase, exactement ceux que lit déjà le front Flutter
 * (lib/features/recruiter_dashboard/data/api_recruiter_dashboard_repository.dart et
 * docs/recruiter-dashboard-r47.md) : le front peut passer du mode mock au mode api sans changement.
 *
 * kpis, trend, profileDistribution, topOffers, activities, priorityActions : champs lus par le front.
 * weeklyInterests et recentApplications : cartes "Intérêts reçus" et "Candidatures reçues" de la maquette
 * company_dashboard.png (le front devra ajouter leur lecture dans son mapper).
 * summary : les mêmes chiffres en nombres bruts, pratiques pour les tests, Swagger ou un autre client.
 */
public record RecruiterDashboardResponse(
        String period,
        Long jobId,
        long companyId,
        OffsetDateTime generatedAt,
        Summary summary,
        List<Kpi> kpis,
        List<TrendPoint> trend,
        List<WeeklyValue> weeklyInterests,
        List<DistributionItem> profileDistribution,
        List<ReceivedApplication> recentApplications,
        List<OfferPerformance> topOffers,
        List<Activity> activities,
        List<PriorityAction> priorityActions) {

    /**
     * Chiffres bruts de la période (sauf activeOffers, pendingApplications et upcomingInterviews,
     * qui décrivent l'état actuel).
     *
     * @param interested       likes reçus de candidats sur les offres (table job_swipes)
     * @param profilesReviewed profils de candidats évalués par l'entreprise (table candidate_swipes)
     * @param avgResponseDays  délai moyen entre une candidature et sa première lecture (viewed_at), en jours ;
     *                         null si aucune candidature n'a encore été lue
     * @param views            vues des offres : toujours 0, la base n'enregistre pas encore les vues
     */
    public record Summary(
            long applications,
            long interested,
            long matches,
            long profilesReviewed,
            long activeOffers,
            long pendingApplications,
            long upcomingInterviews,
            Double avgResponseDays,
            long views) {
    }

    /**
     * Une carte KPI. value est une chaîne déjà formatée pour l'affichage (ex. "12", "2,8 j").
     * deltaPercent : évolution par rapport à la période précédente de même durée ; null pour "all"
     * ou si la période précédente était à 0.
     */
    public record Kpi(String id, String label, String value, Double deltaPercent, String deltaLabel) {
    }

    /**
     * Un point de la courbe. 7d et 30d : un point par jour ; all : un point par semaine (12 semaines).
     * previousApplications = candidatures du même jour (ou de la même semaine) sur la période précédente.
     */
    public record TrendPoint(
            String date,
            long applications,
            long matches,
            long interviews,
            long likes,
            long previousApplications) {
    }

    /**
     * Carte "Intérêts reçus" : likes de candidats par semaine, toujours sur les 8 dernières semaines
     * (S1 = la plus ancienne, S8 = la semaine en cours), quelle que soit la période choisie.
     */
    public record WeeklyValue(String label, String weekStart, long value) {
    }

    /**
     * Une ligne de la carte "Candidatures reçues".
     *
     * @param lastName           nom complet, ou seulement l'initiale si le candidat a choisi de masquer son nom
     * @param education          dernière formation, ex. "Informatique · Sorbonne Université" (null si aucune)
     * @param availableFrom      date de disponibilité au format AAAA-MM-JJ (null si non renseignée)
     * @param compatibilityScore % de compatibilité si un match existe pour cette offre, sinon null
     */
    public record ReceivedApplication(
            long applicationId,
            long candidateId,
            String firstName,
            String lastName,
            String photoUrl,
            String education,
            String availableFrom,
            long jobOfferId,
            String jobTitle,
            String status,
            String statusLabel,
            Integer compatibilityScore,
            OffsetDateTime createdAt,
            String whenLabel) {
    }

    /** Répartition des candidatures de la période par statut (seuls les statuts non vides). */
    public record DistributionItem(String label, long value) {
    }

    /**
     * Performance d'une offre. conversionRate = % des candidatures arrivées à l'entretien ou acceptées.
     * lastActivity = libellé lisible ("il y a 3 j") ; lastActivityAt = la même date au format ISO.
     */
    public record OfferPerformance(
            long id,
            String title,
            String location,
            String contract,
            String status,
            String statusCode,
            long applications,
            long interested,
            long matches,
            long interviews,
            long views,
            double conversionRate,
            String lastActivity,
            OffsetDateTime lastActivityAt,
            String tagline) {
    }

    /** kind : application, match, interview ou offer (valeurs attendues par le front). */
    public record Activity(String kind, String title, String subtitle, String whenLabel, OffsetDateTime occurredAt) {
    }

    public record PriorityAction(String title, String subtitle, String actionLabel) {
    }
}
