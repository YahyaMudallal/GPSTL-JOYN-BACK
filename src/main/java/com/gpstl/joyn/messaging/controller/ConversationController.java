package com.gpstl.joyn.messaging.controller;

import com.gpstl.joyn.joboffer.dto.ApiError;
import com.gpstl.joyn.messaging.dto.ConversationItem;
import com.gpstl.joyn.messaging.dto.ConversationList;
import com.gpstl.joyn.messaging.dto.MessageItem;
import com.gpstl.joyn.messaging.dto.MessagePage;
import com.gpstl.joyn.messaging.dto.ReadReceipt;
import com.gpstl.joyn.messaging.exception.MessagingException;
import com.gpstl.joyn.messaging.service.MessagingService;
import com.gpstl.joyn.messaging.service.MessagingStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * CO26 – Messagerie entre un candidat et les recruteurs d'une entreprise.
 *
 *   GET   /api/v1/conversations                     mes conversations (+ dernier message, non lus)
 *   POST  /api/v1/conversations                     ouvrir la conversation d'une candidature
 *   GET   /api/v1/conversations/{id}                le détail d'une conversation
 *   GET   /api/v1/conversations/{id}/messages       les messages, par pages (curseur ?before=)
 *   POST  /api/v1/conversations/{id}/messages       envoyer un message
 *   PATCH /api/v1/conversations/{id}/read           marquer les messages reçus comme lus
 *   GET   /api/v1/conversations/stream              temps réel (SSE)
 *
 * Sécurité : SecurityConfig exige un token CANDIDATE ou RECRUITER (sans token -> 401, admin -> 403).
 * L'utilisateur vient uniquement du claim "sub" du token ; le service vérifie qu'il participe
 * bien à la conversation (sinon 403).
 */
@RestController
@RequestMapping("/api/v1/conversations")
@Tag(name = "Messaging", description = "Conversations et messages entre candidats et recruteurs")
@SecurityRequirement(name = "bearerAuth")
public class ConversationController {

    private final MessagingService messagingService;
    private final MessagingStreamService streamService;

    public ConversationController(MessagingService messagingService, MessagingStreamService streamService) {
        this.messagingService = messagingService;
        this.streamService = streamService;
    }

    @GetMapping
    @Operation(summary = "List my conversations",
            description = "Most recently active first, with the last message and the unread count of each.")
    @ApiResponse(responseCode = "200", description = "Conversations of the authenticated user")
    @ApiResponse(responseCode = "401", description = "Token missing, invalid or expired", content = @Content)
    @ApiResponse(responseCode = "403", description = "Not a candidate or a recruiter, or account suspended",
            content = @Content)
    public ConversationList listConversations(@AuthenticationPrincipal Jwt jwt) {
        return messagingService.listConversations(userId(jwt));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Open (or get) the conversation of an application",
            description = """
                    Body: {"application_id": 52} (recommended; "applicationId" also accepted).
                    A recruiter may send {"candidateId": 7, "jobId": 34} instead: the latest application of
                    this candidate on the company's offers is used.
                    201 when the conversation is created, 200 when it already existed.
                    A candidate can open it only when the application is at INTERVIEW or ACCEPTED.""")
    @ApiResponse(responseCode = "201", description = "Conversation created")
    @ApiResponse(responseCode = "200", description = "Conversation already existed")
    @ApiResponse(responseCode = "400", description = "application_id missing or invalid",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Application of someone else / another company, "
            + "or candidate before the interview step", content = @Content)
    @ApiResponse(responseCode = "404", description = "Application not found", content = @Content)
    @ApiResponse(responseCode = "409", description = "The candidate withdrew this application", content = @Content)
    public ResponseEntity<ConversationItem> openConversation(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) Map<String, Object> body) {

        Map<String, Object> values = body == null ? Map.of() : body;
        Long applicationId = id(values, "application_id", "applicationId");
        Long candidateId = id(values, "candidateId", "candidate_id");
        Long jobId = id(values, "jobId", "job_id");

        MessagingService.OpenResult result =
                messagingService.openConversation(userId(jwt), applicationId, candidateId, jobId);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.conversation());
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Real-time stream (Server-Sent Events)",
            description = "Keeps the connection open and pushes 'message' and 'read' events "
                    + "for all the conversations of the authenticated user.")
    public SseEmitter stream(@AuthenticationPrincipal Jwt jwt) {
        long userId = userId(jwt);
        messagingService.checkAccount(userId); // compte actif, candidat ou recruteur (sinon 401 / 403 / 404)
        return streamService.subscribe(userId);
    }

    @GetMapping("/{conversationId}")
    @Operation(summary = "Get one conversation")
    @ApiResponse(responseCode = "200", description = "The conversation")
    @ApiResponse(responseCode = "403", description = "Not a participant", content = @Content)
    @ApiResponse(responseCode = "404", description = "Conversation not found", content = @Content)
    public ConversationItem getConversation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("conversationId") Long conversationId) {
        return messagingService.getConversation(userId(jwt), positive(conversationId));
    }

    @GetMapping("/{conversationId}/messages")
    @Operation(summary = "List the messages of a conversation (cursor pagination)",
            description = "Returns the most recent messages before the cursor. "
                    + "To load older messages, call again with ?before=<nextCursor>.")
    @ApiResponse(responseCode = "200", description = "A page of messages")
    @ApiResponse(responseCode = "400", description = "Invalid before, limit or order",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Not a participant", content = @Content)
    @ApiResponse(responseCode = "404", description = "Conversation not found", content = @Content)
    public MessagePage listMessages(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("conversationId") Long conversationId,
            @Parameter(description = "Cursor: id of the oldest message already loaded")
            @RequestParam(required = false) Long before,
            @Parameter(description = "Page size, 1 to 100 (default 30)")
            @RequestParam(required = false) Integer limit,
            @Parameter(description = "asc (oldest to newest, default) or desc (newest first)")
            @RequestParam(required = false) String order) {
        return messagingService.listMessages(userId(jwt), positive(conversationId), before, limit, order);
    }

    @PostMapping(path = "/{conversationId}/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Send a message",
            description = "Body: {\"text\": \"Bonjour\", \"clientId\": \"tmp-1\"}. "
                    + "\"content\" is accepted instead of \"text\". clientId is optional and sent back as is.")
    @ApiResponse(responseCode = "201", description = "Message sent")
    @ApiResponse(responseCode = "400", description = "Empty or too long message (max 5000 characters)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Not a participant", content = @Content)
    @ApiResponse(responseCode = "404", description = "Conversation not found", content = @Content)
    public ResponseEntity<MessageItem> sendMessage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("conversationId") Long conversationId,
            @RequestBody(required = false) Map<String, Object> body) {

        Map<String, Object> values = body == null ? Map.of() : body;
        Object rawText = values.containsKey("text") ? values.get("text") : values.get("content");
        if (rawText != null && !(rawText instanceof String)) {
            throw MessagingException.badRequest("text", "text must be a string");
        }
        Object rawClientId = values.get("clientId");

        MessageItem message = messagingService.sendMessage(userId(jwt), positive(conversationId),
                (String) rawText, rawClientId == null ? null : String.valueOf(rawClientId));
        return ResponseEntity.status(HttpStatus.CREATED).body(message);
    }

    @PatchMapping("/{conversationId}/read")
    @Operation(summary = "Mark the received messages of a conversation as read",
            description = "No body needed (the front may send {\"read\": true}).")
    @ApiResponse(responseCode = "200", description = "Messages marked as read")
    @ApiResponse(responseCode = "403", description = "Not a participant", content = @Content)
    @ApiResponse(responseCode = "404", description = "Conversation not found", content = @Content)
    public ReadReceipt markAsRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("conversationId") Long conversationId,
            @RequestBody(required = false) Map<String, Object> ignoredBody) {
        return messagingService.markAsRead(userId(jwt), positive(conversationId));
    }

    // ------------------------------------------------------------------ outils

    /** users.id vient uniquement du claim "sub" du token vérifié. */
    private static long userId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw MessagingException.unauthorized("Authentication is required");
        }
        try {
            long id = Long.parseLong(jwt.getSubject());
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException ex) {
            throw MessagingException.unauthorized("Invalid authenticated user identity");
        }
    }

    private static long positive(Long conversationId) {
        if (conversationId == null || conversationId <= 0) {
            throw MessagingException.badRequest("conversationId", "conversationId must be a positive integer");
        }
        return conversationId;
    }

    /**
     * Lit un id dans le corps JSON, sous l'un des noms acceptés.
     * Accepte un nombre (52) ou un texte numérique ("52"), car le front envoie parfois les ids en texte.
     */
    private static Long id(Map<String, Object> body, String... names) {
        for (String name : names) {
            Object value = body.get(name);
            if (value == null) {
                continue;
            }
            try {
                long id = value instanceof Number number ? number.longValue() : Long.parseLong(value.toString().trim());
                if (id <= 0) {
                    throw new NumberFormatException();
                }
                return id;
            } catch (NumberFormatException ex) {
                throw MessagingException.badRequest(name, name + " must be a positive integer");
            }
        }
        return null;
    }
}
