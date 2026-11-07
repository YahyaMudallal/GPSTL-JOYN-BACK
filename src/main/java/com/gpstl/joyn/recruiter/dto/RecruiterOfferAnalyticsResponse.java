package com.gpstl.joyn.recruiter.dto;

import com.gpstl.joyn.recruiter.dto.RecruiterDashboardResponse.DistributionItem;
import com.gpstl.joyn.recruiter.dto.RecruiterDashboardResponse.Kpi;
import com.gpstl.joyn.recruiter.dto.RecruiterDashboardResponse.OfferPerformance;
import com.gpstl.joyn.recruiter.dto.RecruiterDashboardResponse.PriorityAction;
import com.gpstl.joyn.recruiter.dto.RecruiterDashboardResponse.TrendPoint;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Réponse de GET /api/v1/recruiters/dashboard/offers/{jobId} (R49) : la performance d'UNE offre.
 * Mêmes noms que ceux lus par le mapper Flutter (fetchOfferAnalytics) :
 * offer, kpis, trend, sources, pipeline, skills, actions.
 *
 * @param sources  origine des candidatures : directe ou après un match
 * @param pipeline candidatures de la période par étape (Nouvelles, Lues, En revue, Entretien, Acceptées)
 * @param skills   compétences demandées par l'offre, avec le % de candidats qui les ont
 * @param actions  choses à faire sur cette offre (candidatures en attente, entretiens à confirmer)
 */
public record RecruiterOfferAnalyticsResponse(
        String period,
        OffsetDateTime generatedAt,
        OfferPerformance offer,
        List<Kpi> kpis,
        List<TrendPoint> trend,
        List<DistributionItem> sources,
        List<DistributionItem> pipeline,
        List<DistributionItem> skills,
        List<PriorityAction> actions) {
}
