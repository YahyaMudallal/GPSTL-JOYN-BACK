package com.gpstl.joyn.recruiter.service;

import com.gpstl.joyn.common.dto.DashboardPeriod;
import com.gpstl.joyn.common.exception.DashboardException;
import com.gpstl.joyn.recruiter.dto.RecruiterDashboardResponse;
import com.gpstl.joyn.recruiter.dto.RecruiterDashboardResponse.Activity;
import com.gpstl.joyn.recruiter.dto.RecruiterDashboardResponse.DistributionItem;
import com.gpstl.joyn.recruiter.dto.RecruiterDashboardResponse.Kpi;
import com.gpstl.joyn.recruiter.dto.RecruiterDashboardResponse.OfferPerformance;
import com.gpstl.joyn.recruiter.dto.RecruiterDashboardResponse.PriorityAction;
import com.gpstl.joyn.recruiter.dto.RecruiterDashboardResponse.ReceivedApplication;
import com.gpstl.joyn.recruiter.dto.RecruiterDashboardResponse.Summary;
import com.gpstl.joyn.recruiter.dto.RecruiterDashboardResponse.TrendPoint;
import com.gpstl.joyn.recruiter.dto.RecruiterDashboardResponse.WeeklyValue;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tableau de bord du recruteur connecté (R49).
 *
 * Toutes les requêtes passent par la même liste d'offres, "scope" :
 * les offres (non supprimées) de L'ENTREPRISE du recruteur, ou une seule offre si job_id est donné.
 * Un recruteur ne peut donc jamais compter les candidatures d'une autre entreprise.
 *
 * Comme pour CA36, on utilise du SQL de comptage (NamedParameterJdbcTemplate) : les tables
 * job_swipes, candidate_swipes, matches et interviews n'ont pas encore d'entité Java.
 */
@Service
public class RecruiterDashboardService {

    private static final int ACTIVITY_LIMIT = 10;
    private static final int RECENT_APPLICATIONS_LIMIT = 5;
    /** Carte "Intérêts reçus" de la maquette : S1 à S8. */
    private static final int INTEREST_WEEKS = 8;
    private static final int TOP_OFFERS_LIMIT = 50;
    /** Pour la période "all" : courbe sur les 12 dernières semaines. */
    private static final int ALL_PERIOD_WEEKS = 12;

    /** Offres prises en compte, réutilisées par chaque requête. :job = 0 veut dire "toutes les offres". */
    private static final String SCOPE = """
            WITH scope AS (
                SELECT id
                FROM job_offers
                WHERE company_id = :company
                  AND deleted_at IS NULL
                  AND (:job = 0 OR id = :job)
            )
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public RecruiterDashboardService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public RecruiterDashboardResponse getDashboard(long userId, DashboardPeriod period, Long jobId) {
        long companyId = findActiveRecruiterCompanyId(userId);
        if (jobId != null) {
            checkOfferBelongsToCompany(jobId, companyId);
        }

        Instant now = Instant.now();
        Instant from = period.start(now);
        // Période précédente de même durée (ex. 30d : du 60e au 30e jour). Vide pour "all".
        Instant previousFrom = period.isAll() ? from : from.minus(Duration.ofDays(period.days()));

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("company", companyId)
                .addValue("job", jobId == null ? 0L : jobId)
                .addValue("from", utc(from))
                .addValue("previousFrom", utc(previousFrom))
                .addValue("now", utc(now));

        Counters counters = counters(params);
        Summary summary = new Summary(counters.applications, counters.interested, counters.matches,
                counters.profilesReviewed, counters.activeOffers, counters.pendingApplications,
                counters.upcomingInterviews, counters.avgResponseDays, 0);

        return new RecruiterDashboardResponse(
                period.code(),
                jobId,
                companyId,
                utc(now),
                summary,
                kpis(counters, period),
                trend(params, period),
                weeklyInterests(params),
                profileDistribution(params),
                recentApplications(params, now),
                topOffers(params, jobId != null, now),
                activities(params, now),
                priorityActions(counters, jobId != null));
    }

    // ------------------------------------------------------------------ identité et droits

    /** users.id (claim "sub") -> entreprise du recruteur. Le compte est relu en base. */
    private long findActiveRecruiterCompanyId(long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT u.role, u.status, rp.company_id
                FROM users u
                LEFT JOIN recruiter_profiles rp ON rp.user_id = u.id
                WHERE u.id = :uid""", Map.of("uid", userId));

        if (rows.isEmpty()) {
            throw DashboardException.unauthorized("Authenticated user does not exist");
        }
        Map<String, Object> row = rows.get(0);
        if (!"ACTIVE".equals(row.get("status"))) {
            throw DashboardException.forbidden("Account is suspended");
        }
        if (!"RECRUITER".equals(row.get("role"))) {
            throw DashboardException.forbidden("Only recruiters have a recruiter dashboard");
        }
        Object companyId = row.get("company_id");
        if (companyId == null) {
            throw DashboardException.notFound(null, "Recruiter profile not found");
        }
        return ((Number) companyId).longValue();
    }

    /** job_id inconnu ou supprimé -> 404 ; offre d'une autre entreprise -> 403. */
    private void checkOfferBelongsToCompany(long jobId, long companyId) {
        List<Long> owners = jdbc.queryForList(
                "SELECT company_id FROM job_offers WHERE id = :job AND deleted_at IS NULL",
                Map.of("job", jobId), Long.class);
        if (owners.isEmpty()) {
            throw DashboardException.notFound("job_id", "Job offer not found");
        }
        if (owners.get(0) != companyId) {
            throw DashboardException.forbidden("This job offer belongs to another company");
        }
    }

    // ------------------------------------------------------------------ compteurs globaux

    private record Counters(long applications, long previousApplications,
                            long interested, long previousInterested,
                            long matches, long previousMatches,
                            long profilesReviewed, long activeOffers, long pendingApplications,
                            long upcomingInterviews, long proposedInterviews, Double avgResponseDays) {
    }

    private Counters counters(MapSqlParameterSource params) {
        return jdbc.queryForObject(SCOPE + """
                , offer_interviews AS (
                    SELECT i.*
                    FROM interviews i
                    LEFT JOIN applications a ON a.id = i.application_id
                    LEFT JOIN matches m ON m.id = i.match_id
                    WHERE COALESCE(a.job_offer_id, m.job_offer_id) IN (SELECT id FROM scope)
                )
                SELECT
                  (SELECT COUNT(*) FROM applications a
                    WHERE a.job_offer_id IN (SELECT id FROM scope) AND a.created_at >= :from) AS applications,
                  (SELECT COUNT(*) FROM applications a
                    WHERE a.job_offer_id IN (SELECT id FROM scope)
                      AND a.created_at >= :previousFrom AND a.created_at < :from) AS previous_applications,

                  (SELECT COUNT(*) FROM job_swipes s
                    WHERE s.job_offer_id IN (SELECT id FROM scope) AND s.action = 'LIKE'
                      AND s.created_at >= :from) AS interested,
                  (SELECT COUNT(*) FROM job_swipes s
                    WHERE s.job_offer_id IN (SELECT id FROM scope) AND s.action = 'LIKE'
                      AND s.created_at >= :previousFrom AND s.created_at < :from) AS previous_interested,

                  (SELECT COUNT(*) FROM matches m
                    WHERE m.job_offer_id IN (SELECT id FROM scope) AND m.created_at >= :from) AS matches,
                  (SELECT COUNT(*) FROM matches m
                    WHERE m.job_offer_id IN (SELECT id FROM scope)
                      AND m.created_at >= :previousFrom AND m.created_at < :from) AS previous_matches,

                  (SELECT COUNT(*) FROM candidate_swipes cs
                    WHERE cs.job_offer_id IN (SELECT id FROM scope) AND cs.created_at >= :from) AS profiles_reviewed,

                  (SELECT COUNT(*) FROM job_offers jo
                    WHERE jo.id IN (SELECT id FROM scope) AND jo.status = 'PUBLISHED') AS active_offers,
                  (SELECT COUNT(*) FROM applications a
                    JOIN application_statuses st ON st.id = a.status_id
                    WHERE a.job_offer_id IN (SELECT id FROM scope) AND st.code = 'PENDING') AS pending_applications,

                  (SELECT COUNT(*) FROM offer_interviews
                    WHERE status IN ('PROPOSED', 'CONFIRMED') AND scheduled_at >= :now) AS upcoming_interviews,
                  (SELECT COUNT(*) FROM offer_interviews
                    WHERE status = 'PROPOSED' AND scheduled_at >= :now) AS proposed_interviews,

                  (SELECT AVG(EXTRACT(EPOCH FROM (a.viewed_at - a.created_at))) / 86400.0
                    FROM applications a
                    WHERE a.job_offer_id IN (SELECT id FROM scope)
                      AND a.viewed_at IS NOT NULL AND a.created_at >= :from) AS avg_response_days
                """, params, (rs, i) -> {
            BigDecimal avg = rs.getBigDecimal("avg_response_days");
            return new Counters(
                    rs.getLong("applications"), rs.getLong("previous_applications"),
                    rs.getLong("interested"), rs.getLong("previous_interested"),
                    rs.getLong("matches"), rs.getLong("previous_matches"),
                    rs.getLong("profiles_reviewed"), rs.getLong("active_offers"),
                    rs.getLong("pending_applications"), rs.getLong("upcoming_interviews"),
                    rs.getLong("proposed_interviews"),
                    avg == null ? null : Math.round(avg.doubleValue() * 10) / 10.0);
        });
    }

    /** Les cartes du haut de l'écran (maquette company_dashboard.png). */
    private static List<Kpi> kpis(Counters c, DashboardPeriod period) {
        String comparison = period.isAll() ? null : "vs période précédente";
        return List.of(
                new Kpi("applications", "Candidatures reçues", String.valueOf(c.applications),
                        delta(c.applications, c.previousApplications, period), comparison),
                new Kpi("interested", "Intérêts reçus", String.valueOf(c.interested),
                        delta(c.interested, c.previousInterested, period), comparison),
                new Kpi("matches", "Matchs", String.valueOf(c.matches),
                        delta(c.matches, c.previousMatches, period),
                        c.upcomingInterviews + plural(c.upcomingInterviews, " entretien planifié", " entretiens planifiés")),
                new Kpi("active_offers", "Offres actives", String.valueOf(c.activeOffers), null, null),
                new Kpi("pending_applications", "Candidatures en attente", String.valueOf(c.pendingApplications),
                        null, null),
                new Kpi("avg_response_time", "Délai de réponse moyen",
                        c.avgResponseDays == null ? "—" : String.format(Locale.FRANCE, "%.1f j", c.avgResponseDays),
                        null, null));
    }

    /** Évolution en % ; null si on ne peut pas comparer (période "all" ou période précédente à 0). */
    private static Double delta(long current, long previous, DashboardPeriod period) {
        if (period.isAll() || previous == 0) {
            return null;
        }
        return Math.round((current - previous) * 1000.0 / previous) / 10.0;
    }

    // ------------------------------------------------------------------ courbe

    private List<TrendPoint> trend(MapSqlParameterSource params, DashboardPeriod period) {
        boolean weekly = period.isAll();
        int stepDays = weekly ? 7 : 1;
        int buckets = weekly ? ALL_PERIOD_WEEKS : period.days();
        MapSqlParameterSource trendParams = new MapSqlParameterSource(params.getValues())
                .addValue("unit", weekly ? "week" : "day")
                .addValue("stepDays", stepDays)
                .addValue("buckets", buckets)
                .addValue("shiftDays", weekly ? 0 : period.days());

        return jdbc.query(SCOPE + """
                , buckets AS (
                    SELECT gs AS bucket_start, gs + make_interval(days => :stepDays) AS bucket_end
                    FROM generate_series(
                        date_trunc(CAST(:unit AS text), CAST(:now AS timestamptz))
                            - make_interval(days => :stepDays * (:buckets - 1)),
                        date_trunc(CAST(:unit AS text), CAST(:now AS timestamptz)),
                        make_interval(days => :stepDays)) AS gs
                )
                SELECT to_char(b.bucket_start, 'YYYY-MM-DD') AS day,
                  (SELECT COUNT(*) FROM applications a
                    WHERE a.job_offer_id IN (SELECT id FROM scope)
                      AND a.created_at >= b.bucket_start AND a.created_at < b.bucket_end) AS applications,
                  (SELECT COUNT(*) FROM matches m
                    WHERE m.job_offer_id IN (SELECT id FROM scope)
                      AND m.created_at >= b.bucket_start AND m.created_at < b.bucket_end) AS matches,
                  (SELECT COUNT(*) FROM interviews i
                    LEFT JOIN applications a ON a.id = i.application_id
                    LEFT JOIN matches m ON m.id = i.match_id
                    WHERE COALESCE(a.job_offer_id, m.job_offer_id) IN (SELECT id FROM scope)
                      AND i.created_at >= b.bucket_start AND i.created_at < b.bucket_end) AS interviews,
                  (SELECT COUNT(*) FROM job_swipes s
                    WHERE s.job_offer_id IN (SELECT id FROM scope) AND s.action = 'LIKE'
                      AND s.created_at >= b.bucket_start AND s.created_at < b.bucket_end) AS likes,
                  (SELECT COUNT(*) FROM applications a
                    WHERE a.job_offer_id IN (SELECT id FROM scope)
                      AND a.created_at >= b.bucket_start - make_interval(days => :shiftDays)
                      AND a.created_at < b.bucket_end - make_interval(days => :shiftDays)) AS previous_applications
                FROM buckets b
                ORDER BY b.bucket_start
                """, trendParams, (rs, i) -> new TrendPoint(
                rs.getString("day"),
                rs.getLong("applications"),
                rs.getLong("matches"),
                rs.getLong("interviews"),
                rs.getLong("likes"),
                // Pas de "période précédente" pour all.
                weekly ? 0 : rs.getLong("previous_applications")));
    }

    /** Carte "Intérêts reçus" : likes par semaine sur les 8 dernières semaines (S1 ... S8). */
    private List<WeeklyValue> weeklyInterests(MapSqlParameterSource params) {
        MapSqlParameterSource weekParams = new MapSqlParameterSource(params.getValues())
                .addValue("weeks", INTEREST_WEEKS);

        return jdbc.query(SCOPE + """
                , weeks AS (
                    SELECT gs AS week_start, gs + make_interval(days => 7) AS week_end
                    FROM generate_series(
                        date_trunc('week', CAST(:now AS timestamptz)) - make_interval(weeks => :weeks - 1),
                        date_trunc('week', CAST(:now AS timestamptz)),
                        make_interval(days => 7)) AS gs
                )
                SELECT to_char(w.week_start, 'YYYY-MM-DD') AS week_start,
                  (SELECT COUNT(*) FROM job_swipes s
                    WHERE s.job_offer_id IN (SELECT id FROM scope) AND s.action = 'LIKE'
                      AND s.created_at >= w.week_start AND s.created_at < w.week_end) AS likes
                FROM weeks w
                ORDER BY w.week_start
                """, weekParams, (rs, i) -> new WeeklyValue("S" + (i + 1), rs.getString("week_start"), rs.getLong("likes")));
    }

    // ------------------------------------------------------------------ répartition

    private List<DistributionItem> profileDistribution(MapSqlParameterSource params) {
        return jdbc.query(SCOPE + """
                SELECT st.label, COUNT(*) AS n
                FROM applications a
                JOIN application_statuses st ON st.id = a.status_id
                WHERE a.job_offer_id IN (SELECT id FROM scope) AND a.created_at >= :from
                GROUP BY st.id, st.label
                ORDER BY st.id
                """, params, (rs, i) -> new DistributionItem(rs.getString("label"), rs.getLong("n")));
    }

    // ------------------------------------------------------------------ détail par offre

    /**
     * Sans job_id : les offres publiées de l'entreprise, les plus demandées d'abord.
     * Avec job_id : uniquement cette offre, quel que soit son statut.
     */
    private List<OfferPerformance> topOffers(MapSqlParameterSource params, boolean singleOffer, Instant now) {
        MapSqlParameterSource offerParams = new MapSqlParameterSource(params.getValues())
                .addValue("singleOffer", singleOffer)
                .addValue("limit", TOP_OFFERS_LIMIT);

        return jdbc.query(SCOPE + """
                SELECT jo.id, jo.title, jo.location, ct.label AS contract, jo.status,
                  (SELECT COUNT(*) FROM applications a
                    WHERE a.job_offer_id = jo.id AND a.created_at >= :from) AS applications,
                  (SELECT COUNT(*) FROM job_swipes s
                    WHERE s.job_offer_id = jo.id AND s.action = 'LIKE' AND s.created_at >= :from) AS interested,
                  (SELECT COUNT(*) FROM matches m
                    WHERE m.job_offer_id = jo.id AND m.created_at >= :from) AS matches,
                  (SELECT COUNT(*) FROM interviews i
                    LEFT JOIN applications a ON a.id = i.application_id
                    LEFT JOIN matches m ON m.id = i.match_id
                    WHERE COALESCE(a.job_offer_id, m.job_offer_id) = jo.id AND i.created_at >= :from) AS interviews,
                  (SELECT COUNT(*) FROM applications a
                    JOIN application_statuses st ON st.id = a.status_id
                    WHERE a.job_offer_id = jo.id AND a.created_at >= :from
                      AND st.code IN ('INTERVIEW', 'ACCEPTED')) AS advanced,
                  GREATEST(jo.updated_at,
                           (SELECT MAX(a.created_at) FROM applications a WHERE a.job_offer_id = jo.id),
                           (SELECT MAX(s.created_at) FROM job_swipes s WHERE s.job_offer_id = jo.id)) AS last_activity
                FROM job_offers jo
                JOIN contract_types ct ON ct.id = jo.contract_type_id
                WHERE jo.id IN (SELECT id FROM scope)
                  AND (:singleOffer OR jo.status = 'PUBLISHED')
                ORDER BY applications DESC, interested DESC, jo.id DESC
                LIMIT :limit
                """, offerParams, (rs, i) -> {
            long applications = rs.getLong("applications");
            long interested = rs.getLong("interested");
            long matches = rs.getLong("matches");
            long advanced = rs.getLong("advanced");
            double conversion = applications == 0 ? 0 : Math.round(advanced * 1000.0 / applications) / 10.0;
            OffsetDateTime lastActivity = rs.getObject("last_activity", OffsetDateTime.class);
            String statusCode = rs.getString("status");
            return new OfferPerformance(
                    rs.getLong("id"),
                    rs.getString("title"),
                    rs.getString("location") == null ? "—" : rs.getString("location"),
                    rs.getString("contract"),
                    statusLabel(statusCode),
                    statusCode,
                    applications,
                    interested,
                    matches,
                    rs.getLong("interviews"),
                    0,
                    conversion,
                    lastActivity == null ? null : relativeLabel(lastActivity.toInstant(), now),
                    lastActivity,
                    interested + plural(interested, " intéressé", " intéressés")
                            + " · " + matches + plural(matches, " match", " matchs"));
        });
    }

    // ------------------------------------------------------------------ dernières candidatures

    /**
     * Carte "Candidatures reçues" : les 5 dernières candidatures de la période.
     * Le nom de famille est réduit à l'initiale si le candidat a coché "masquer mon nom" (show_last_name).
     */
    private List<ReceivedApplication> recentApplications(MapSqlParameterSource params, Instant now) {
        MapSqlParameterSource applicationParams = new MapSqlParameterSource(params.getValues())
                .addValue("limit", RECENT_APPLICATIONS_LIMIT);

        return jdbc.query(SCOPE + """
                SELECT a.id AS application_id, cp.id AS candidate_id, cp.first_name, cp.last_name,
                       cp.show_last_name, cp.photo_url, cp.available_from,
                       jo.id AS job_offer_id, jo.title AS job_title,
                       st.code AS status, st.label AS status_label, a.created_at,
                       m.compatibility_score,
                       (SELECT e.field_of_study || ' · ' || e.school_name
                        FROM candidate_educations e
                        WHERE e.candidate_id = cp.id
                        ORDER BY e.currently_studying DESC, e.end_year DESC NULLS FIRST, e.id DESC
                        LIMIT 1) AS education
                FROM applications a
                JOIN candidate_profiles cp ON cp.id = a.candidate_id
                JOIN job_offers jo ON jo.id = a.job_offer_id
                JOIN application_statuses st ON st.id = a.status_id
                LEFT JOIN matches m ON m.candidate_id = a.candidate_id AND m.job_offer_id = a.job_offer_id
                WHERE a.job_offer_id IN (SELECT id FROM scope)
                  AND a.created_at >= :from
                ORDER BY a.created_at DESC, a.id DESC
                LIMIT :limit
                """, applicationParams, (rs, i) -> {
            String lastName = rs.getString("last_name");
            if (!rs.getBoolean("show_last_name") && lastName != null && !lastName.isBlank()) {
                lastName = lastName.charAt(0) + ".";
            }
            java.sql.Date availableFrom = rs.getDate("available_from");
            OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
            return new ReceivedApplication(
                    rs.getLong("application_id"),
                    rs.getLong("candidate_id"),
                    rs.getString("first_name"),
                    lastName,
                    rs.getString("photo_url"),
                    rs.getString("education"),
                    availableFrom == null ? null : availableFrom.toLocalDate().toString(),
                    rs.getLong("job_offer_id"),
                    rs.getString("job_title"),
                    rs.getString("status"),
                    rs.getString("status_label"),
                    rs.getObject("compatibility_score", Integer.class),
                    createdAt,
                    relativeLabel(createdAt.toInstant(), now));
        });
    }

    // ------------------------------------------------------------------ activité récente

    private List<Activity> activities(MapSqlParameterSource params, Instant now) {
        MapSqlParameterSource activityParams = new MapSqlParameterSource(params.getValues())
                .addValue("limit", ACTIVITY_LIMIT);

        return jdbc.query(SCOPE + """
                SELECT t.kind, t.title, t.occurred_at, jo.title AS offer_title,
                       cp.first_name, cp.last_name
                FROM (
                    SELECT 'application' AS kind, 'Nouvelle candidature' AS title,
                           a.created_at AS occurred_at, a.job_offer_id, a.candidate_id
                    FROM applications a
                    WHERE a.job_offer_id IN (SELECT id FROM scope) AND a.created_at >= :from

                    UNION ALL
                    SELECT 'match', 'Nouveau match', m.created_at, m.job_offer_id, m.candidate_id
                    FROM matches m
                    WHERE m.job_offer_id IN (SELECT id FROM scope) AND m.created_at >= :from

                    UNION ALL
                    SELECT 'interview', 'Entretien planifié', i.created_at,
                           COALESCE(a.job_offer_id, m.job_offer_id), COALESCE(a.candidate_id, m.candidate_id)
                    FROM interviews i
                    LEFT JOIN applications a ON a.id = i.application_id
                    LEFT JOIN matches m ON m.id = i.match_id
                    WHERE COALESCE(a.job_offer_id, m.job_offer_id) IN (SELECT id FROM scope)
                      AND i.created_at >= :from

                    UNION ALL
                    SELECT 'offer', 'Offre publiée', jo.published_at, jo.id, CAST(NULL AS BIGINT)
                    FROM job_offers jo
                    WHERE jo.id IN (SELECT id FROM scope) AND jo.published_at >= :from
                ) t
                JOIN job_offers jo ON jo.id = t.job_offer_id
                LEFT JOIN candidate_profiles cp ON cp.id = t.candidate_id
                ORDER BY t.occurred_at DESC
                LIMIT :limit
                """, activityParams, (rs, i) -> {
            OffsetDateTime occurredAt = rs.getObject("occurred_at", OffsetDateTime.class);
            String offerTitle = rs.getString("offer_title");
            String candidate = shortName(rs.getString("first_name"), rs.getString("last_name"));
            return new Activity(
                    rs.getString("kind"),
                    rs.getString("title"),
                    candidate == null ? offerTitle : candidate + " · " + offerTitle,
                    relativeLabel(occurredAt.toInstant(), now),
                    occurredAt);
        });
    }

    // ------------------------------------------------------------------ actions prioritaires

    private static List<PriorityAction> priorityActions(Counters c, boolean singleOffer) {
        List<PriorityAction> actions = new ArrayList<>();
        if (c.pendingApplications > 0) {
            actions.add(new PriorityAction(
                    c.pendingApplications + plural(c.pendingApplications,
                            " candidature en attente", " candidatures en attente"),
                    "Réponds vite pour garder les candidats motivés",
                    "Voir les candidatures"));
        }
        if (c.proposedInterviews > 0) {
            actions.add(new PriorityAction(
                    c.proposedInterviews + plural(c.proposedInterviews,
                            " entretien à confirmer", " entretiens à confirmer"),
                    "Le candidat attend une confirmation",
                    "Voir les entretiens"));
        }
        if (!singleOffer && c.activeOffers == 0) {
            actions.add(new PriorityAction(
                    "Aucune offre publiée",
                    "Publie une offre pour commencer à recevoir des candidatures",
                    "Créer une offre"));
        }
        return actions;
    }

    // ------------------------------------------------------------------ petits outils

    /** "Lina M." : prénom + initiale du nom, pour ne pas afficher le nom complet dans le fil. */
    private static String shortName(String firstName, String lastName) {
        if (firstName == null) {
            return null;
        }
        return lastName == null || lastName.isBlank() ? firstName : firstName + " " + lastName.charAt(0) + ".";
    }

    private static String statusLabel(String code) {
        return switch (code) {
            case "PUBLISHED" -> "Publiée";
            case "DRAFT" -> "Brouillon";
            case "CLOSED" -> "Clôturée";
            case "ARCHIVED" -> "Archivée";
            default -> code;
        };
    }

    /** "il y a 3 j", "il y a 5 h"... */
    static String relativeLabel(Instant when, Instant now) {
        Duration elapsed = Duration.between(when, now);
        if (elapsed.toMinutes() < 1) {
            return "à l'instant";
        }
        if (elapsed.toHours() < 1) {
            return "il y a " + elapsed.toMinutes() + " min";
        }
        if (elapsed.toDays() < 1) {
            return "il y a " + elapsed.toHours() + " h";
        }
        return "il y a " + elapsed.toDays() + " j";
    }

    private static String plural(long count, String singular, String pluralForm) {
        return count > 1 ? pluralForm : singular;
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
