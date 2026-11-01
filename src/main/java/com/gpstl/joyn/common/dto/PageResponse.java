package com.gpstl.joyn.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Format commun des listes paginées de l'API (A66, et réutilisable pour A80 / A81).
 * Exemple :
 * {"data": [...], "pagination": {"current_page": 1, "limit": 20, "total_items": 142, "total_pages": 8}}
 */
public record PageResponse<T>(List<T> data, Pagination pagination) {

    public record Pagination(
            @JsonProperty("current_page") int currentPage,
            int limit,
            @JsonProperty("total_items") long totalItems,
            @JsonProperty("total_pages") int totalPages
    ) {
    }

    /** Construit la réponse à partir d'une page Spring Data (dont les numéros commencent à 0). */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                new Pagination(page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages()));
    }
}
