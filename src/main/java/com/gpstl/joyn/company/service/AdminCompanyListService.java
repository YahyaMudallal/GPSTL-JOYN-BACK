package com.gpstl.joyn.company.service;

import com.gpstl.joyn.common.dto.PageResponse;
import com.gpstl.joyn.company.dto.AdminCompanyItem;
import com.gpstl.joyn.company.exception.InvalidQueryParameterException;
import com.gpstl.joyn.company.model.Company;
import com.gpstl.joyn.company.repository.CompanyRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Liste paginée des entreprises pour le back-office admin (A66) :
 * GET /api/v1/admin/companies?page=1&limit=20&search=...&status=...&sector=...&company_size=...&sort_by=...&order=...
 *
 * Le contrôle "rôle ADMIN" est fait avant d'arriver ici, par SecurityConfig.
 */
@Service
public class AdminCompanyListService {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;

    /** Valeurs autorisées : les mêmes que les contraintes CHECK de la table companies. */
    private static final Set<String> STATUSES = Set.of("NOT_VERIFIED", "VERIFIED", "REJECTED");
    private static final Set<String> COMPANY_SIZES = Set.of("MICRO", "SMALL", "MEDIUM", "LARGE");

    /** Colonnes de tri autorisées : nom du paramètre -> nom du champ Java. */
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "created_at", "createdAt",
            "name", "name");

    private final CompanyRepository companyRepository;

    public AdminCompanyListService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminCompanyItem> listCompanies(int page, int limit, String search, String status,
                                                         String sector, String companySize,
                                                         String sortBy, String order) {
        // 1. Vérifier les paramètres : une valeur invalide donne un 400 qui dit quel paramètre corriger.
        if (page < 1) {
            throw new InvalidQueryParameterException("page", "page doit être un entier supérieur ou égal à 1");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidQueryParameterException("limit", "limit doit être compris entre 1 et " + MAX_LIMIT);
        }
        String normalizedStatus = normalizeEnum("status", status, STATUSES);
        String normalizedSize = normalizeEnum("company_size", companySize, COMPANY_SIZES);

        String sortField = SORT_FIELDS.get(sortBy == null ? "" : sortBy.trim().toLowerCase(Locale.ROOT));
        if (sortField == null) {
            throw new InvalidQueryParameterException("sort_by", "Valeurs acceptées : created_at, name");
        }
        String normalizedOrder = order == null ? "" : order.trim().toLowerCase(Locale.ROOT);
        if (!normalizedOrder.equals("asc") && !normalizedOrder.equals("desc")) {
            throw new InvalidQueryParameterException("order", "Valeurs acceptées : asc, desc");
        }

        // 2. Tri + pagination faits par la base (LIMIT / OFFSET) : on ne charge jamais toute la table.
        //    Le tri secondaire sur l'id garantit un ordre stable d'une page à l'autre.
        Sort.Direction direction = normalizedOrder.equals("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, sortField).and(Sort.by(direction, "id"));
        PageRequest pageRequest = PageRequest.of(page - 1, limit, sort);

        // 3. Requête avec les filtres demandés, puis transformation en DTO.
        Specification<Company> filters = buildFilters(search, normalizedStatus, sector, normalizedSize);
        Page<AdminCompanyItem> result = companyRepository.findAll(filters, pageRequest).map(AdminCompanyItem::from);
        return PageResponse.from(result);
    }

    /**
     * Construit la clause WHERE dynamique : chaque filtre n'est ajouté que s'il est renseigné.
     * search : recherche insensible à la casse dans le nom, le SIRET et le domaine email.
     */
    private Specification<Company> buildFilters(String search, String status, String sector, String companySize) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                String pattern = "%" + escapeLike(search.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.<String>get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.<String>get("siret")), pattern, '\\'),
                        cb.like(cb.lower(root.<String>get("emailDomain")), pattern, '\\')));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("verificationStatus"), status));
            }
            if (sector != null && !sector.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.<String>get("sector")), sector.trim().toLowerCase(Locale.ROOT)));
            }
            if (companySize != null) {
                predicates.add(cb.equal(root.get("companySize"), companySize));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** Filtre facultatif : null si absent, la valeur en majuscules si elle est autorisée, sinon 400. */
    private static String normalizeEnum(String parameter, String value, Set<String> allowed) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new InvalidQueryParameterException(parameter,
                    "Valeurs acceptées : " + String.join(", ", allowed.stream().sorted().toList()));
        }
        return normalized;
    }

    /** Empêche "%" et "_" tapés par l'utilisateur d'être interprétés comme des jokers SQL. */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
