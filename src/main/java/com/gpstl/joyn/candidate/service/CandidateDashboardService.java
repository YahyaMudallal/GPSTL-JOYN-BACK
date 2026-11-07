package com.gpstl.joyn.candidate.service;

import com.gpstl.joyn.candidate.dto.CandidateDashboardResponse;
import com.gpstl.joyn.candidate.dto.CandidateDashboardResponse.Activity;
import com.gpstl.joyn.candidate.dto.CandidateDashboardResponse.Applications;
import com.gpstl.joyn.candidate.dto.CandidateDashboardResponse.Conversations;
import com.gpstl.joyn.candidate.dto.CandidateDashboardResponse.Interviews;
import com.gpstl.joyn.candidate.dto.CandidateDashboardResponse.Matches;
import com.gpstl.joyn.candidate.dto.CandidateDashboardResponse.NextInterview;
import com.gpstl.joyn.candidate.dto.CandidateDashboardResponse.Pipeline;
import com.gpstl.joyn.candidate.dto.CandidateDashboardResponse.RecentMatch;
import com.gpstl.joyn.candidate.dto.CandidateDashboardResponse.Swipes;
import com.gpstl.joyn.common.dto.DashboardPeriod;
import com.gpstl.joyn.common.exception.DashboardException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tableau de bord du candidat connecté (CA36).
 *
 * Pourquoi du SQL "à la main" (NamedParameterJdbcTemplate) et pas des repositories JPA ?
 * Les tables lues ici (job_swipes, matches, conversations, messages, interviews) n'ont pas
 * encore d'entité Java, et un dashboard ne fait que compter : quelques requêtes COUNT
 * sont plus simples et plus rapides que charger des objets pour les compter en Java.
 *
 * Toutes les requêtes sont filtrées sur l'id du candidat trouvé à partir du token :
 * un candidat ne peut jamais voir les chiffres d'un autre.
 */
@Service
public class CandidateDashboardService {

    /** Statuts qui comptent comme "candidature en cours". */
    private static final Set<String> ACTIVE_STATUSES = Set.of("PENDING", "VIEWED", "IN_REVIEW", "INTERVIEW");
    private static final int RECENT_ACTIVITY_LIMIT = 10;
    private static final int RECENT_MATCHES_LIMIT = 3;
    /** "Nouvelles offres" = publiées depuis 7 jours (bouton "Découvrir les N nouvelles offres"). */
    private static final int NEW_OFFERS_DAYS = 7;

    private final NamedParameterJdbcTemplate jdbc;

    public CandidateDashboardService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public CandidateDashboardResponse getDashboard(long userId, DashboardPeriod period) {
        long candidateId = findActiveCandidateId(userId);

        Instant now = Instant.now();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cid", candidateId)
                .addValue("uid", userId)
                .addValue("from", utc(period.start(now)))
                .addValue("now", utc(now))
                .addValue("newOffersSince", utc(now.minus(Duration.ofDays(NEW_OFFERS_DAYS))));

        return new CandidateDashboardResponse(
                period.code(),
                utc(now),
                pipeline(params),
                applications(params),
                swipes(params),
                matches(params),
                recentMatches(params),
                conversations(params),
                interviews(params),
                newOffersCount(params),
                recentActivities(params));
    }

    // ------------------------------------------------------------------ identité

    /**
     * users.id (claim "sub") -> candidate_profiles.id.
     * Le compte est relu en base : un token émis avant une suspension ne suffit pas.
     */
    private long findActiveCandidateId(long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT u.role, u.status, cp.id AS candidate_id
                FROM users u
                LEFT JOIN candidate_profiles cp ON cp.user_id = u.id
                WHERE u.id = :uid""", Map.of("uid", userId));

        if (rows.isEmpty()) {
            throw DashboardException.unauthorized("Authenticated user does not exist");
        }
        Map<String, Object> row = rows.get(0);
        if (!"ACTIVE".equals(row.get("status"))) {
            throw DashboardException.forbidden("Account is suspended");
        }
        if (!"CANDIDATE".equals(row.get("role"))) {
            throw DashboardException.forbidden("Only candidates have a candidate dashboard");
        }
        Object candidateId = row.get("candidate_id");
        if (candidateId == null) {
            throw DashboardException.notFound(null, "Candidate profile not found");
        }
        return ((Number) candidateId).longValue();
    }

    // ------------------------------------------------------------------ compteurs

    /**
     * Carte "Candidatures en cours" : situation d'aujourd'hui, comme sur la maquette.
     * "Intéressé" = offres likées, toujours publiées, sur lesquelles le candidat n'a pas encore postulé
     * (une fois qu'il postule, l'offre passe dans "Envoyée" et n'est pas comptée deux fois).
     */
    private Pipeline pipeline(MapSqlParameterSource params) {
        return jdbc.queryForObject("""
                SELECT
                  (SELECT COUNT(*)
                   FROM job_swipes s
                   JOIN job_offers jo ON jo.id = s.job_offer_id
                   WHERE s.candidate_id = :cid
                     AND s.action = 'LIKE'
                     AND jo.status = 'PUBLISHED'
                     AND jo.deleted_at IS NULL
                     AND NOT EXISTS (SELECT 1 FROM applications a
                                     WHERE a.candidate_id = :cid AND a.job_offer_id = s.job_offer_id)
                  ) AS interested,
                  COUNT(*) FILTER (WHERE st.code = 'PENDING') AS sent,
                  COUNT(*) FILTER (WHERE st.code IN ('VIEWED', 'IN_REVIEW')) AS viewed,
                  COUNT(*) FILTER (WHERE st.code = 'INTERVIEW') AS interview
                FROM applications a
                JOIN application_statuses st ON st.id = a.status_id
                WHERE a.candidate_id = :cid""", params, (rs, i) -> {
            long interested = rs.getLong("interested");
            long sent = rs.getLong("sent");
            long viewed = rs.getLong("viewed");
            long interview = rs.getLong("interview");
            return new Pipeline(interested + sent + viewed + interview, interested, sent, viewed, interview);
        });
    }

    private Applications applications(MapSqlParameterSource params) {
        // LEFT JOIN depuis application_statuses : chaque statut apparaît, même avec 0 candidature.
        Map<String, Long> byStatus = new LinkedHashMap<>();
        jdbc.query("""
                SELECT s.code, COUNT(a.id) AS n
                FROM application_statuses s
                LEFT JOIN applications a
                       ON a.status_id = s.id
                      AND a.candidate_id = :cid
                      AND a.created_at >= :from
                GROUP BY s.id, s.code
                ORDER BY s.id""", params, rs -> {
            byStatus.put(rs.getString("code"), rs.getLong("n"));
        });
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();

        // "En cours" = état actuel, toutes périodes confondues.
        Long active = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM applications a
                JOIN application_statuses s ON s.id = a.status_id
                WHERE a.candidate_id = :cid
                  AND s.code IN (:activeStatuses)""",
                new MapSqlParameterSource(params.getValues()).addValue("activeStatuses", ACTIVE_STATUSES),
                Long.class);
        return new Applications(total, active == null ? 0 : active, byStatus);
    }

    private Swipes swipes(MapSqlParameterSource params) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FILTER (WHERE action = 'LIKE') AS likes,
                       COUNT(*) FILTER (WHERE action = 'PASS') AS passes
                FROM job_swipes
                WHERE candidate_id = :cid
                  AND created_at >= :from""", params, (rs, i) -> {
            long likes = rs.getLong("likes");
            long passes = rs.getLong("passes");
            return new Swipes(likes + passes, likes, passes);
        });
    }

    private Matches matches(MapSqlParameterSource params) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS total,
                       COUNT(*) FILTER (WHERE status = 'ACTIVE'
                                          AND (expires_at IS NULL OR expires_at > :now)) AS active
                FROM matches
                WHERE candidate_id = :cid
                  AND created_at >= :from""", params,
                (rs, i) -> new Matches(rs.getLong("total"), rs.getLong("active")));
    }

    /** Carte "Nouveaux Matchs" : les 3 derniers matchs de la période, avec le % de compatibilité. */
    private List<RecentMatch> recentMatches(MapSqlParameterSource params) {
        return jdbc.query("""
                SELECT m.id, m.job_offer_id, jo.title, co.name AS company_name, co.logo_url,
                       m.compatibility_score, m.status, m.created_at, c.id AS conversation_id
                FROM matches m
                JOIN job_offers jo ON jo.id = m.job_offer_id
                JOIN companies co ON co.id = jo.company_id
                LEFT JOIN conversations c ON c.match_id = m.id
                WHERE m.candidate_id = :cid
                  AND m.created_at >= :from
                ORDER BY m.created_at DESC, m.id DESC
                LIMIT :limit""",
                new MapSqlParameterSource(params.getValues()).addValue("limit", RECENT_MATCHES_LIMIT),
                (rs, i) -> new RecentMatch(
                        rs.getLong("id"),
                        rs.getLong("job_offer_id"),
                        rs.getString("title"),
                        rs.getString("company_name"),
                        rs.getString("logo_url"),
                        rs.getObject("compatibility_score", Integer.class),
                        rs.getString("status"),
                        rs.getObject("conversation_id", Long.class),
                        rs.getObject("created_at", OffsetDateTime.class)));
    }

    /**
     * Bouton "Découvrir les N nouvelles offres" : offres publiées depuis 7 jours
     * que le candidat n'a encore ni swipées ni postulées.
     */
    private long newOffersCount(MapSqlParameterSource params) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM job_offers jo
                WHERE jo.status = 'PUBLISHED'
                  AND jo.deleted_at IS NULL
                  AND jo.published_at >= :newOffersSince
                  AND (jo.expires_at IS NULL OR jo.expires_at > :now)
                  AND NOT EXISTS (SELECT 1 FROM job_swipes s
                                  WHERE s.candidate_id = :cid AND s.job_offer_id = jo.id)
                  AND NOT EXISTS (SELECT 1 FROM applications a
                                  WHERE a.candidate_id = :cid AND a.job_offer_id = jo.id)""",
                params, Long.class);
        return count == null ? 0 : count;
    }

    /**
     * Une conversation appartient au candidat si elle vient de SA candidature ou de SON match.
     * Messages non lus = envoyés par quelqu'un d'autre et read_at encore vide.
     */
    private Conversations conversations(MapSqlParameterSource params) {
        return jdbc.queryForObject("""
                WITH my_conversations AS (
                    SELECT c.id, c.created_at
                    FROM conversations c
                    LEFT JOIN applications a ON a.id = c.application_id
                    LEFT JOIN matches m ON m.id = c.match_id
                    WHERE a.candidate_id = :cid OR m.candidate_id = :cid
                )
                SELECT (SELECT COUNT(*) FROM my_conversations WHERE created_at >= :from) AS total,
                       (SELECT COUNT(*)
                        FROM messages msg
                        JOIN my_conversations mc ON mc.id = msg.conversation_id
                        WHERE msg.read_at IS NULL
                          AND msg.sender_user_id IS DISTINCT FROM :uid) AS unread""", params,
                (rs, i) -> new Conversations(rs.getLong("total"), rs.getLong("unread")));
    }

    /** Entretiens à venir (proposés ou confirmés), via une candidature ou un match du candidat. */
    private Interviews interviews(MapSqlParameterSource params) {
        String upcomingInterviews = """
                FROM interviews i
                LEFT JOIN applications a ON a.id = i.application_id
                LEFT JOIN matches m ON m.id = i.match_id
                JOIN job_offers jo ON jo.id = COALESCE(a.job_offer_id, m.job_offer_id)
                JOIN companies co ON co.id = jo.company_id
                WHERE (a.candidate_id = :cid OR m.candidate_id = :cid)
                  AND i.status IN ('PROPOSED', 'CONFIRMED')
                  AND i.scheduled_at >= :now""";

        Long upcoming = jdbc.queryForObject("SELECT COUNT(*) " + upcomingInterviews, params, Long.class);

        List<NextInterview> next = jdbc.query("""
                SELECT i.id, i.scheduled_at, i.duration_minutes, i.mode, i.status, i.location_or_link,
                       jo.id AS job_offer_id, jo.title, co.name AS company_name,
                       -- conversation de la candidature, sinon celle du match (bouton Ouvrir la discussion)
                       COALESCE((SELECT c.id FROM conversations c WHERE c.application_id = i.application_id),
                                (SELECT c.id FROM conversations c WHERE c.match_id = i.match_id)) AS conversation_id
                """ + upcomingInterviews + """

                ORDER BY i.scheduled_at, i.id
                LIMIT 1""", params, (rs, i) -> new NextInterview(
                rs.getLong("id"),
                rs.getObject("scheduled_at", OffsetDateTime.class),
                rs.getInt("duration_minutes"),
                rs.getString("mode"),
                rs.getString("status"),
                rs.getString("location_or_link"),
                rs.getLong("job_offer_id"),
                rs.getString("title"),
                rs.getString("company_name"),
                rs.getObject("conversation_id", Long.class)));

        return new Interviews(upcoming == null ? 0 : upcoming, next.isEmpty() ? null : next.get(0));
    }

    /**
     * Les 10 derniers événements de la période, toutes sources mélangées (UNION ALL),
     * du plus récent au plus ancien.
     */
    private List<Activity> recentActivities(MapSqlParameterSource params) {
        return jdbc.query("""
                SELECT t.type, t.occurred_at, jo.id AS job_offer_id, jo.title, co.name AS company_name
                FROM (
                    SELECT 'APPLICATION_SENT' AS type, a.created_at AS occurred_at, a.job_offer_id
                    FROM applications a
                    WHERE a.candidate_id = :cid AND a.created_at >= :from

                    UNION ALL
                    SELECT 'APPLICATION_VIEWED', a.viewed_at, a.job_offer_id
                    FROM applications a
                    WHERE a.candidate_id = :cid AND a.viewed_at >= :from

                    UNION ALL
                    SELECT 'OFFER_LIKED', s.created_at, s.job_offer_id
                    FROM job_swipes s
                    WHERE s.candidate_id = :cid AND s.action = 'LIKE' AND s.created_at >= :from

                    UNION ALL
                    SELECT 'MATCH', m.created_at, m.job_offer_id
                    FROM matches m
                    WHERE m.candidate_id = :cid AND m.created_at >= :from

                    UNION ALL
                    SELECT 'MESSAGE_RECEIVED', msg.created_at, COALESCE(a.job_offer_id, m.job_offer_id)
                    FROM messages msg
                    JOIN conversations c ON c.id = msg.conversation_id
                    LEFT JOIN applications a ON a.id = c.application_id
                    LEFT JOIN matches m ON m.id = c.match_id
                    WHERE (a.candidate_id = :cid OR m.candidate_id = :cid)
                      AND msg.sender_user_id IS DISTINCT FROM :uid
                      AND msg.created_at >= :from

                    UNION ALL
                    SELECT 'INTERVIEW_PLANNED', i.created_at, COALESCE(a.job_offer_id, m.job_offer_id)
                    FROM interviews i
                    LEFT JOIN applications a ON a.id = i.application_id
                    LEFT JOIN matches m ON m.id = i.match_id
                    WHERE (a.candidate_id = :cid OR m.candidate_id = :cid)
                      AND i.created_at >= :from
                ) t
                JOIN job_offers jo ON jo.id = t.job_offer_id
                JOIN companies co ON co.id = jo.company_id
                ORDER BY t.occurred_at DESC
                LIMIT :limit""",
                new MapSqlParameterSource(params.getValues()).addValue("limit", RECENT_ACTIVITY_LIMIT),
                (rs, i) -> new Activity(
                        rs.getString("type"),
                        rs.getObject("occurred_at", OffsetDateTime.class),
                        rs.getLong("job_offer_id"),
                        rs.getString("title"),
                        rs.getString("company_name")));
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
