package com.gpstl.joyn.candidate.service;

import com.gpstl.joyn.candidate.dto.AdminCandidateFilters;
import com.gpstl.joyn.candidate.dto.AdminCandidateItem;
import com.gpstl.joyn.candidate.model.Candidate;
import com.gpstl.joyn.candidate.repository.CandidateRepository;
import com.gpstl.joyn.common.dto.PageResponse;
import com.gpstl.joyn.common.model.User;
import com.gpstl.joyn.common.repository.UserRepository;
import com.gpstl.joyn.company.exception.InvalidQueryParameterException;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Liste paginée des candidats pour le back-office admin (A80) :
 * GET /api/v1/admin/candidates?page=1&limit=20&search=...&status=...&email_verified=...&created_from=...
 *     &created_to=...&last_login_before=...&location=...&visible_to_recruiters=...&has_resume=...&sort_by=...&order=...
 *
 * Le rôle du token est contrôlé par SecurityConfig ; le compte de l'administrateur est relu en base :
 * un token émis avant une suspension ou un changement de rôle ne suffit pas.
 */
@Service
public class AdminCandidateListService {

    static final int MAX_LIMIT = 100;

    /** Les mêmes valeurs que la contrainte CHECK de la colonne users.status. */
    private static final Set<String> STATUSES = Set.of("ACTIVE", "SUSPENDED");

    /** Colonnes de tri autorisées : nom du paramètre -> chemin du champ Java. */
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "created_at", "user.createdAt",
            "email", "user.email",
            "first_name", "firstName",
            "last_name", "lastName");

    private final CandidateRepository candidates;
    private final UserRepository users;

    public AdminCandidateListService(CandidateRepository candidates, UserRepository users) {
        this.candidates = candidates;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminCandidateItem> listCandidates(long adminUserId, int page, int limit,
            AdminCandidateFilters filters, String sortBy, String order) {
        checkAdminAccount(adminUserId);

        // 1. Validation : un paramètre invalide donne un 400 avant toute requête sur les candidats.
        if (page < 1) {
            throw new InvalidQueryParameterException("page", "page doit être un entier supérieur ou égal à 1");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidQueryParameterException("limit", "limit doit être compris entre 1 et " + MAX_LIMIT);
        }
        // L'OFFSET est transmis à la base comme un int : au-delà, la requête échouerait en 500.
        if ((page - 1L) * limit > Integer.MAX_VALUE) {
            throw new InvalidQueryParameterException("page", "page est trop grand");
        }
        String normalizedStatus = normalizeStatus(filters.status());
        checkDate("created_from", filters.createdFrom());
        checkDate("created_to", filters.createdTo());
        checkDate("last_login_before", filters.lastLoginBefore());
        if (filters.createdFrom() != null && filters.createdTo() != null
                && filters.createdFrom().isAfter(filters.createdTo())) {
            throw new InvalidQueryParameterException("created_to", "created_to doit être postérieur ou égal à created_from");
        }
        String sortField = SORT_FIELDS.get(sortBy == null ? "" : sortBy.trim().toLowerCase(Locale.ROOT));
        if (sortField == null) {
            throw new InvalidQueryParameterException("sort_by", "Valeurs acceptées : created_at, email, first_name, last_name");
        }
        String normalizedOrder = order == null ? "" : order.trim().toLowerCase(Locale.ROOT);
        if (!normalizedOrder.equals("asc") && !normalizedOrder.equals("desc")) {
            throw new InvalidQueryParameterException("order", "Valeurs acceptées : asc, desc");
        }

        // 2. Tri + pagination faits par la base (LIMIT / OFFSET).
        //    Le tri secondaire sur l'id garantit un ordre stable d'une page à l'autre.
        Sort.Direction direction = normalizedOrder.equals("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, sortField).and(Sort.by(direction, "id"));
        PageRequest pageRequest = PageRequest.of(page - 1, limit, sort);

        // 3. Requête avec les filtres demandés, puis transformation en DTO.
        return PageResponse.from(candidates.findAll(buildFilters(filters, normalizedStatus), pageRequest)
                .map(AdminCandidateItem::from));
    }

    /** Utilisateur absent : 401 ; compte suspendu ou rôle non autorisé : 403. */
    private void checkAdminAccount(long userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user does not exist"));
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is suspended");
        }
        if (!"ADMIN".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permissions");
        }
    }

    /**
     * Construit la clause WHERE dynamique : chaque filtre n'est ajouté que s'il est renseigné.
     * search : recherche insensible à la casse dans l'email, le prénom et le nom.
     * lastLoginBefore : un candidat qui ne s'est jamais connecté n'a pas de date, il n'est donc pas retenu.
     */
    private Specification<Candidate> buildFilters(AdminCandidateFilters filters, String status) {
        String search = filters.search();
        String location = filters.location();
        return (root, query, cb) -> {
            Join<Candidate, User> user = root.join("user");
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(user.get("role"), "CANDIDATE"));

            if (status != null) {
                predicates.add(cb.equal(user.get("status"), status));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + escapeLike(search.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(user.<String>get("email")), pattern, '\\'),
                        cb.like(cb.lower(root.<String>get("firstName")), pattern, '\\'),
                        cb.like(cb.lower(root.<String>get("lastName")), pattern, '\\')));
            }
            if (filters.emailVerified() != null) {
                predicates.add(filters.emailVerified()
                        ? cb.isNotNull(user.get("emailVerifiedAt"))
                        : cb.isNull(user.get("emailVerifiedAt")));
            }
            if (filters.createdFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(user.<Instant>get("createdAt"), startOfDay(filters.createdFrom())));
            }
            if (filters.createdTo() != null) {
                // Date incluse : tout ce qui précède le début du jour suivant.
                predicates.add(cb.lessThan(user.<Instant>get("createdAt"), startOfDay(filters.createdTo().plusDays(1))));
            }
            if (filters.lastLoginBefore() != null) {
                predicates.add(cb.lessThan(user.<Instant>get("lastLoginAt"), startOfDay(filters.lastLoginBefore())));
            }
            if (location != null && !location.isBlank()) {
                String pattern = "%" + escapeLike(location.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.like(cb.lower(root.<String>get("location")), pattern, '\\'));
            }
            if (filters.visibleToRecruiters() != null) {
                predicates.add(cb.equal(root.get("visibleToRecruiters"), filters.visibleToRecruiters()));
            }
            if (filters.hasResume() != null) {
                predicates.add(filters.hasResume()
                        ? cb.isNotNull(root.get("resumeUrl"))
                        : cb.isNull(root.get("resumeUrl")));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** Filtre facultatif : null si absent, la valeur en majuscules si elle est autorisée, sinon 400. */
    private static String normalizeStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) {
            throw new InvalidQueryParameterException("status", "Valeurs acceptées : ACTIVE, SUSPENDED");
        }
        return normalized;
    }

    /** Une année hors de ce que PostgreSQL accepte (ex. +999999-01-01) donnerait une erreur 500. */
    private static void checkDate(String parameter, LocalDate date) {
        if (date != null && (date.getYear() < 1 || date.getYear() > 9999)) {
            throw new InvalidQueryParameterException(parameter, "L'année doit être comprise entre 1 et 9999");
        }
    }

    private static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /** Empêche "%" et "_" tapés par l'utilisateur d'être interprétés comme des jokers SQL. */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
