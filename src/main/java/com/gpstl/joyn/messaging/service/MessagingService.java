package com.gpstl.joyn.messaging.service;

import com.gpstl.joyn.company.service.CompanyLogoStorage;
import com.gpstl.joyn.messaging.dto.ConversationItem;
import com.gpstl.joyn.messaging.dto.ConversationList;
import com.gpstl.joyn.messaging.dto.MessageItem;
import com.gpstl.joyn.messaging.dto.MessagePage;
import com.gpstl.joyn.messaging.dto.ReadReceipt;
import com.gpstl.joyn.messaging.event.MessagingEvent;
import com.gpstl.joyn.messaging.exception.MessagingException;
import com.gpstl.joyn.messaging.model.Conversation;
import com.gpstl.joyn.messaging.model.Message;
import com.gpstl.joyn.messaging.repository.ConversationRepository;
import com.gpstl.joyn.messaging.repository.MessageRepository;
import com.gpstl.joyn.user.service.UserProfilePictureStorage;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Messagerie entre un candidat et les recruteurs d'une entreprise (CO26).
 *
 * Une conversation est liée à UNE candidature (applications) ou à UN match (matches).
 * Ses participants sont :
 *   - le candidat de cette candidature / de ce match ;
 *   - tous les recruteurs de l'entreprise qui a publié l'offre.
 * Toute autre personne reçoit 403, même si elle connaît l'id de la conversation.
 *
 * Les tables conversations et messages existent depuis V1 : aucune migration n'est nécessaire.
 *
 * Deux outils, chacun pour ce qu'il fait le mieux :
 *   - JPA (entités Conversation et Message, repositories) pour ÉCRIRE : ouvrir une conversation,
 *     envoyer un message, marquer comme lu ;
 *   - SQL (NamedParameterJdbcTemplate) pour LIRE avec des jointures et des comptages : la liste des
 *     conversations avec le dernier message et les non-lus, les pages de messages, les droits d'accès.
 * Dans le SQL, les valeurs passent toujours en paramètres (:id), jamais collées dans le texte.
 */
@Service
public class MessagingService {

    static final int MAX_TEXT_LENGTH = 5000;
    static final int DEFAULT_PAGE_SIZE = 30;
    static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_CONVERSATIONS = 200;

    /** Statuts où le candidat peut lui-même ouvrir la conversation (même règle que le front). */
    private static final Set<String> CANDIDATE_CAN_START = Set.of("INTERVIEW", "ACCEPTED");

    private final NamedParameterJdbcTemplate jdbc;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ApplicationEventPublisher events;
    private final UserProfilePictureStorage profilePictureStorage;
    private final CompanyLogoStorage logoStorage;

    public MessagingService(NamedParameterJdbcTemplate jdbc,
                            ConversationRepository conversationRepository,
                            MessageRepository messageRepository,
                            ApplicationEventPublisher events,
                            UserProfilePictureStorage profilePictureStorage,
                            CompanyLogoStorage logoStorage) {
        this.jdbc = jdbc;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.events = events;
        this.profilePictureStorage = profilePictureStorage;
        this.logoStorage = logoStorage;
    }

    // ================================================================== qui est connecté ?

    /** L'utilisateur connecté : un candidat (candidateId > 0) ou un recruteur (companyId > 0). */
    record Viewer(long userId, String role, long candidateId, long companyId) {
        boolean isCandidate() {
            return "CANDIDATE".equals(role);
        }

        boolean isRecruiter() {
            return "RECRUITER".equals(role);
        }
    }

    /** users.id (claim "sub") -> candidat ou recruteur. Le compte est relu en base à chaque appel. */
    Viewer viewer(long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT u.role, u.status, cp.id AS candidate_id, rp.company_id, co.deleted_at AS company_deleted_at
                FROM users u
                LEFT JOIN candidate_profiles cp ON cp.user_id = u.id
                LEFT JOIN recruiter_profiles rp ON rp.user_id = u.id
                LEFT JOIN companies co ON co.id = rp.company_id
                WHERE u.id = :uid""", Map.of("uid", userId));
        if (rows.isEmpty()) {
            throw MessagingException.unauthorized("Authenticated user does not exist");
        }
        Map<String, Object> row = rows.get(0);
        if (!"ACTIVE".equals(row.get("status"))) {
            throw MessagingException.forbidden("Account is suspended");
        }
        String role = (String) row.get("role");
        long candidateId = row.get("candidate_id") == null ? 0 : ((Number) row.get("candidate_id")).longValue();
        long companyId = row.get("company_id") == null ? 0 : ((Number) row.get("company_id")).longValue();

        if ("CANDIDATE".equals(role) && candidateId == 0) {
            throw MessagingException.notFound(null, "Candidate profile not found");
        }
        if ("RECRUITER".equals(role) && companyId == 0) {
            throw MessagingException.notFound(null, "Recruiter profile not found");
        }
        // Entreprise supprimée : le recruteur perd l'accès à la messagerie (les messages restent en base)
        if ("RECRUITER".equals(role) && row.get("company_deleted_at") != null) {
            throw MessagingException.forbidden("Recruiter company has been deleted");
        }
        if (!"CANDIDATE".equals(role) && !"RECRUITER".equals(role)) {
            throw MessagingException.forbidden("Only candidates and recruiters can use the messaging");
        }
        return new Viewer(userId, role, candidateId, companyId);
    }

    /** Vérifie seulement le compte (utilisé avant d'ouvrir le flux temps réel). */
    @Transactional(readOnly = true)
    public void checkAccount(long userId) {
        viewer(userId);
    }

    // ================================================================== droits sur une conversation

    /** Ce qu'il faut savoir d'une conversation pour vérifier les droits. */
    record ConversationRef(long id, long candidateId, long candidateUserId, long companyId, boolean companyDeleted) {
    }

    /** Charge la conversation et vérifie que l'utilisateur en fait partie : sinon 404 ou 403. */
    ConversationRef checkAccess(long conversationId, Viewer viewer) {
        List<ConversationRef> refs = jdbc.query("""
                SELECT c.id, cp.id AS candidate_id, cp.user_id AS candidate_user_id, jo.company_id,
                       co.deleted_at IS NOT NULL AS company_deleted
                FROM conversations c
                LEFT JOIN applications a ON a.id = c.application_id
                LEFT JOIN matches m ON m.id = c.match_id
                JOIN candidate_profiles cp ON cp.id = COALESCE(a.candidate_id, m.candidate_id)
                JOIN job_offers jo ON jo.id = COALESCE(a.job_offer_id, m.job_offer_id)
                JOIN companies co ON co.id = jo.company_id
                WHERE c.id = :id""", Map.of("id", conversationId), (rs, i) -> new ConversationRef(
                rs.getLong("id"), rs.getLong("candidate_id"), rs.getLong("candidate_user_id"),
                rs.getLong("company_id"), rs.getBoolean("company_deleted")));
        if (refs.isEmpty()) {
            throw MessagingException.notFound("conversationId", "Conversation not found");
        }
        ConversationRef ref = refs.get(0);
        boolean allowed = viewer.isCandidate()
                ? ref.candidateId() == viewer.candidateId()
                : ref.companyId() == viewer.companyId();
        if (!allowed) {
            throw MessagingException.forbidden("You are not a participant of this conversation");
        }
        return ref;
    }

    /** users.id de tous les participants : le candidat + les recruteurs actifs de l'entreprise. */
    Set<Long> participants(ConversationRef ref) {
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(ref.candidateUserId());
        ids.addAll(jdbc.queryForList("""
                SELECT u.id
                FROM recruiter_profiles rp
                JOIN users u ON u.id = rp.user_id
                WHERE rp.company_id = :company AND u.status = 'ACTIVE'""",
                Map.of("company", ref.companyId()), Long.class));
        return ids;
    }

    // ================================================================== GET /conversations

    /** Les conversations de l'utilisateur, la plus récemment active d'abord. */
    @Transactional(readOnly = true)
    public ConversationList listConversations(long userId) {
        Viewer viewer = viewer(userId);
        List<ConversationItem> items = jdbc.query(CONVERSATION_SELECT + """
                WHERE (:candidateId <> 0 AND cp.id = :candidateId)
                   OR (:companyId <> 0 AND jo.company_id = :companyId)
                ORDER BY COALESCE(last.created_at, c.created_at) DESC, c.id DESC
                LIMIT :limit""", viewerParams(viewer).addValue("limit", MAX_CONVERSATIONS), conversationMapper(viewer));
        long totalUnread = items.stream().mapToLong(ConversationItem::unread).sum();
        return new ConversationList(items, totalUnread);
    }

    // ================================================================== GET /conversations/{id}

    @Transactional(readOnly = true)
    public ConversationItem getConversation(long userId, long conversationId) {
        Viewer viewer = viewer(userId);
        checkAccess(conversationId, viewer);
        return findItem(viewer, conversationId);
    }

    private ConversationItem findItem(Viewer viewer, long conversationId) {
        return jdbc.queryForObject(CONVERSATION_SELECT + " WHERE c.id = :conversationId",
                viewerParams(viewer).addValue("conversationId", conversationId), conversationMapper(viewer));
    }

    // ================================================================== POST /conversations

    /** Résultat d'une ouverture : created = false si la conversation existait déjà (réponse 200 au lieu de 201). */
    public record OpenResult(ConversationItem conversation, boolean created) {
    }

    /**
     * Ouvre (ou retrouve) la conversation d'une candidature.
     *
     * @param applicationId la candidature (recommandé)
     * @param candidateId   pour un recruteur seulement, à la place de applicationId : on prend la candidature
     *                      la plus récente de ce candidat sur une offre de son entreprise (jobId pour choisir l'offre)
     */
    @Transactional
    public OpenResult openConversation(long userId, Long applicationId, Long candidateId, Long jobId) {
        Viewer viewer = viewer(userId);
        long appId = applicationId != null ? applicationId : findApplicationOfCandidate(viewer, candidateId, jobId);

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT a.id, a.candidate_id, jo.company_id, st.code, co.deleted_at AS company_deleted_at
                FROM applications a
                JOIN job_offers jo ON jo.id = a.job_offer_id
                JOIN companies co ON co.id = jo.company_id
                JOIN application_statuses st ON st.id = a.status_id
                WHERE a.id = :id""", Map.of("id", appId));
        if (rows.isEmpty()) {
            throw MessagingException.notFound("application_id", "Application not found");
        }
        Map<String, Object> app = rows.get(0);
        long appCandidate = ((Number) app.get("candidate_id")).longValue();
        long appCompany = ((Number) app.get("company_id")).longValue();
        String status = (String) app.get("code");

        if (viewer.isCandidate()) {
            if (appCandidate != viewer.candidateId()) {
                throw MessagingException.forbidden("This application is not yours");
            }
            if (!CANDIDATE_CAN_START.contains(status)) {
                throw MessagingException.forbidden(
                        "The conversation opens when the application reaches the interview step");
            }
        } else {
            if (appCompany != viewer.companyId()) {
                throw MessagingException.forbidden("This application belongs to another company");
            }
            if ("WITHDRAWN".equals(status)) {
                throw MessagingException.conflict("application_id", "The candidate withdrew this application");
            }
        }

        boolean alreadyExists = conversationRepository.existsByApplicationId(appId);
        // Entreprise supprimée : la conversation existante reste lisible, mais on n'en crée plus
        if (!alreadyExists && app.get("company_deleted_at") != null) {
            throw MessagingException.forbidden("The company has been deleted");
        }
        long conversationId = ensureConversationForApplication(appId);
        return new OpenResult(findItem(viewer, conversationId), !alreadyExists);
    }

    /** Recruteur qui envoie seulement candidateId (format actuel du front recruteur). */
    private long findApplicationOfCandidate(Viewer viewer, Long candidateId, Long jobId) {
        if (candidateId == null) {
            throw MessagingException.badRequest("application_id", "application_id is required");
        }
        if (!viewer.isRecruiter()) {
            throw MessagingException.badRequest("application_id", "application_id is required");
        }
        List<Long> ids = jdbc.queryForList("""
                SELECT a.id
                FROM applications a
                JOIN job_offers jo ON jo.id = a.job_offer_id
                WHERE a.candidate_id = :candidate
                  AND jo.company_id = :company
                  AND (:job = 0 OR jo.id = :job)
                ORDER BY a.created_at DESC, a.id DESC
                LIMIT 1""",
                new MapSqlParameterSource()
                        .addValue("candidate", candidateId)
                        .addValue("company", viewer.companyId())
                        .addValue("job", jobId == null ? 0L : jobId),
                Long.class);
        if (ids.isEmpty()) {
            throw MessagingException.notFound("candidateId",
                    "This candidate has no application on your company's offers");
        }
        return ids.get(0);
    }

    /**
     * Crée la conversation d'une candidature si elle n'existe pas encore, et renvoie son id.
     * Utilisée par POST /conversations ET par l'ouverture automatique (AutomaticConversationListener).
     * Jamais de doublon, même si deux appels arrivent en même temps (voir ConversationRepository).
     */
    public long ensureConversationForApplication(long applicationId) {
        return conversationRepository.findOrCreateForApplication(applicationId).getId();
    }

    /**
     * Crée la conversation d'un match si elle n'existe pas encore, et renvoie son id.
     * Prête pour le futur module de matching : il lui suffira de publier MatchCreated.
     */
    public long ensureConversationForMatch(long matchId) {
        return conversationRepository.findOrCreateForMatch(matchId).getId();
    }

    // ================================================================== GET /conversations/{id}/messages

    /**
     * Messages d'une conversation, par pages, avec un curseur.
     * On prend les `limit` messages les plus récents dont l'id est inférieur à `before`.
     * L'id des messages augmente avec le temps : trier par id = trier par date d'envoi.
     *
     * @param before id du message le plus ancien déjà affiché (null = commencer par les plus récents)
     * @param order  "asc" (du plus ancien au plus récent, prêt à afficher) ou "desc" (antéchronologique)
     */
    @Transactional(readOnly = true)
    public MessagePage listMessages(long userId, long conversationId, Long before, Integer limit, String order) {
        Viewer viewer = viewer(userId);
        checkAccess(conversationId, viewer);

        int size = limit == null ? DEFAULT_PAGE_SIZE : limit;
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw MessagingException.badRequest("limit", "limit must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (before != null && before < 1) {
            throw MessagingException.badRequest("before", "before must be a positive message id");
        }
        String direction = order == null || order.isBlank() ? "asc" : order.trim().toLowerCase();
        if (!direction.equals("asc") && !direction.equals("desc")) {
            throw MessagingException.badRequest("order", "Accepted values: asc, desc");
        }

        // On demande un message de plus que la taille de page : s'il existe, il reste une page après.
        List<MessageItem> rows = jdbc.query(MESSAGE_SELECT + """
                WHERE msg.conversation_id = :conversationId
                  AND (:before = 0 OR msg.id < :before)
                ORDER BY msg.id DESC
                LIMIT :fetch""",
                new MapSqlParameterSource()
                        .addValue("conversationId", conversationId)
                        .addValue("before", before == null ? 0L : before)
                        .addValue("fetch", size + 1),
                messageMapper(viewer.userId(), null));

        boolean hasMore = rows.size() > size;
        List<MessageItem> page = new ArrayList<>(hasMore ? rows.subList(0, size) : rows);
        Long nextCursor = hasMore ? page.get(page.size() - 1).id() : null;
        if (direction.equals("asc")) {
            Collections.reverse(page);
        }
        return new MessagePage(page, nextCursor, hasMore);
    }

    // ================================================================== POST /conversations/{id}/messages

    @Transactional
    public MessageItem sendMessage(long userId, long conversationId, String text, String clientId) {
        Viewer viewer = viewer(userId);
        ConversationRef ref = checkAccess(conversationId, viewer);
        if (ref.companyDeleted()) {
            throw MessagingException.forbidden("The company has been deleted; this conversation is read-only");
        }

        String content = text == null ? "" : text.strip();
        if (content.isEmpty()) {
            throw MessagingException.badRequest("text", "The message cannot be empty");
        }
        if (content.length() > MAX_TEXT_LENGTH) {
            throw MessagingException.badRequest("text", "The message cannot exceed " + MAX_TEXT_LENGTH + " characters");
        }
        if (clientId != null && clientId.length() > 100) {
            throw MessagingException.badRequest("clientId", "clientId cannot exceed 100 characters");
        }

        // Écriture avec JPA : le message est enregistré, la conversation remonte en tête de liste.
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> MessagingException.notFound("conversationId", "Conversation not found"));
        Message saved = messageRepository.saveAndFlush(new Message(conversation, userId, content));
        conversation.touch();
        conversationRepository.saveAndFlush(conversation);

        // Relecture en SQL pour renvoyer le message avec le rôle et le prénom de l'expéditeur.
        MessageItem message = jdbc.queryForObject(MESSAGE_SELECT + " WHERE msg.id = :id",
                Map.of("id", saved.getId()), messageMapper(userId, clientId));

        // Temps réel : envoyé aux participants APRÈS le commit (voir MessagingStreamService).
        events.publishEvent(MessagingEvent.newMessage(message, participants(ref)));
        return message;
    }

    // ================================================================== PATCH /conversations/{id}/read

    /**
     * Marque comme lus les messages reçus de la conversation :
     * pour un candidat, ceux des recruteurs ; pour un recruteur, ceux du candidat.
     */
    @Transactional
    public ReadReceipt markAsRead(long userId, long conversationId) {
        Viewer viewer = viewer(userId);
        ConversationRef ref = checkAccess(conversationId, viewer);

        OffsetDateTime now = OffsetDateTime.now();
        int marked = viewer.isRecruiter()
                ? messageRepository.markAsReadFromSender(conversationId, ref.candidateUserId(), now)
                : messageRepository.markAsReadNotSentBy(conversationId, userId, now);

        if (marked > 0) {
            events.publishEvent(MessagingEvent.read(conversationId, userId, participants(ref)));
        }
        return new ReadReceipt(conversationId, marked, 0);
    }

    // ================================================================== SQL partagé

    /**
     * Une ligne par conversation, avec l'offre, l'entreprise, le candidat, le dernier message
     * et le nombre de messages non lus POUR l'utilisateur connecté (:uid).
     * Les messages ont une seule date de lecture (read_at) : côté entreprise, dès qu'un recruteur
     * a lu un message du candidat, il est lu pour toute l'équipe.
     */
    private static final String CONVERSATION_SELECT = """
            SELECT c.id, c.application_id, c.match_id, c.created_at, c.updated_at, cp.user_id AS candidate_user_id,
                   jo.id AS job_offer_id, jo.title AS offer_title,
                   co.id AS company_id, co.name AS company_name, co.logo_url,
                   cp.id AS candidate_id, cp.first_name, cp.last_name, cp.show_last_name, cp.photo_url,
                   last.content AS last_message, last.created_at AS last_message_at,
                   (SELECT COUNT(*) FROM messages x
                     WHERE x.conversation_id = c.id
                       AND x.read_at IS NULL
                       -- non lu POUR MOI : un recruteur compte les messages du candidat (pas ceux de ses
                       -- collègues) ; un candidat compte les messages qu'il n'a pas envoyés lui-même.
                       AND CASE WHEN :companyId <> 0 THEN x.sender_user_id = cp.user_id
                                ELSE x.sender_user_id IS DISTINCT FROM :uid END) AS unread
            FROM conversations c
            LEFT JOIN applications a ON a.id = c.application_id
            LEFT JOIN matches m ON m.id = c.match_id
            JOIN candidate_profiles cp ON cp.id = COALESCE(a.candidate_id, m.candidate_id)
            JOIN job_offers jo ON jo.id = COALESCE(a.job_offer_id, m.job_offer_id)
            JOIN companies co ON co.id = jo.company_id
            LEFT JOIN LATERAL (
                SELECT msg.content, msg.created_at
                FROM messages msg
                WHERE msg.conversation_id = c.id
                ORDER BY msg.id DESC
                LIMIT 1
            ) last ON TRUE
            """;

    /** Un message avec le rôle et le prénom de l'expéditeur. */
    private static final String MESSAGE_SELECT = """
            SELECT msg.id, msg.conversation_id, msg.sender_user_id, msg.content, msg.created_at, msg.read_at,
                   u.role AS sender_role,
                   COALESCE(scp.first_name, srp.first_name) AS sender_name
            FROM messages msg
            LEFT JOIN users u ON u.id = msg.sender_user_id
            LEFT JOIN candidate_profiles scp ON scp.user_id = msg.sender_user_id
            LEFT JOIN recruiter_profiles srp ON srp.user_id = msg.sender_user_id
            """;

    private static MapSqlParameterSource viewerParams(Viewer viewer) {
        return new MapSqlParameterSource()
                .addValue("uid", viewer.userId())
                .addValue("candidateId", viewer.isCandidate() ? viewer.candidateId() : 0L)
                .addValue("companyId", viewer.isRecruiter() ? viewer.companyId() : 0L);
    }

    private RowMapper<ConversationItem> conversationMapper(Viewer viewer) {
        return (rs, i) -> {
            String candidateName = displayName(rs.getString("first_name"), rs.getString("last_name"),
                    rs.getBoolean("show_last_name"));
            String companyName = rs.getString("company_name");
            String photoUrl = profilePictureStorage.createPresignedUrlIfValid(rs.getString("photo_url"));
            return new ConversationItem(
                    rs.getLong("id"),
                    viewer.isRecruiter() ? candidateName : companyName,
                    rs.getObject("application_id", Long.class),
                    rs.getObject("match_id", Long.class),
                    rs.getLong("job_offer_id"),
                    rs.getString("offer_title"),
                    rs.getLong("company_id"),
                    companyName,
                    logoStorage.createPresignedUrlIfValid(rs.getString("logo_url")), // R84 : clé -> URL
                    rs.getLong("candidate_id"),
                    candidateName,
                    photoUrl,
                    rs.getString("last_message"),
                    rs.getObject("last_message_at", OffsetDateTime.class),
                    rs.getLong("unread"),
                    rs.getObject("created_at", OffsetDateTime.class),
                    rs.getObject("updated_at", OffsetDateTime.class));
        };
    }

    private static RowMapper<MessageItem> messageMapper(long viewerUserId, String clientId) {
        return (rs, i) -> {
            Long sender = rs.getObject("sender_user_id", Long.class);
            return new MessageItem(
                    rs.getLong("id"),
                    rs.getLong("conversation_id"),
                    sender,
                    rs.getString("sender_role"),
                    rs.getString("sender_name"),
                    sender != null && sender == viewerUserId,
                    rs.getString("content"),
                    rs.getObject("created_at", OffsetDateTime.class),
                    rs.getObject("read_at", OffsetDateTime.class),
                    clientId);
        };
    }

    /** "Lina Martin", ou "Lina M." si le candidat a choisi de masquer son nom de famille. */
    static String displayName(String firstName, String lastName, boolean showLastName) {
        String first = firstName == null ? "" : firstName.trim();
        if (lastName == null || lastName.isBlank()) {
            return first;
        }
        return showLastName ? first + " " + lastName.trim() : first + " " + lastName.trim().charAt(0) + ".";
    }
}
