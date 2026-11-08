package com.gpstl.joyn.recruiter.dto;

import com.gpstl.joyn.recruiter.dto.RecruiterDashboardResponse.DistributionItem;
import com.gpstl.joyn.recruiter.dto.RecruiterDashboardResponse.TrendPoint;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Réponse de GET /api/v1/recruiters/dashboard/insights (R49) : l'écran "analyse" du front.
 * Mêmes noms que ceux lus par le mapper Flutter (fetchAnalytics) : trend, funnel, channels, heatmap, surface.
 *
 * @param funnel   entonnoir : Intéressés -> Candidatures -> Lues -> Entretiens -> Acceptées
 * @param channels origine des candidatures : directe ou après un match
 * @param heatmap  candidatures reçues par jour de la semaine et par tranche horaire (heure de Paris)
 * @param surface  graphique 3D prévu par le front ; vide, car la base n'a pas encore ces données
 */
public record RecruiterInsightsResponse(
        String period,
        Long jobId,
        long companyId,
        OffsetDateTime generatedAt,
        List<TrendPoint> trend,
        List<DistributionItem> funnel,
        List<DistributionItem> channels,
        Heatmap heatmap,
        Surface surface) {

    /** rows = jours (Lun ... Dim), columns = tranches horaires, values[jour][tranche] = nombre de candidatures. */
    public record Heatmap(List<String> rows, List<String> columns, List<List<Long>> values) {
    }

    public record Surface(List<String> xLabels, List<String> yLabels, List<List<Long>> values) {
    }
}
