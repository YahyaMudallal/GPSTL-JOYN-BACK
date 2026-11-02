package com.gpstl.joyn.recruiter.service;

import com.gpstl.joyn.common.dto.PageResponse;
import com.gpstl.joyn.recruiter.dto.AdminRecruiterItem;
import com.gpstl.joyn.recruiter.model.Recruiter;
import com.gpstl.joyn.recruiter.repository.RecruiterRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

@Service
public class AdminRecruiterListService {

    private static final Set<String> ALLOWED_USER_STATUSES = Set.of("ACTIVE", "SUSPENDED");

    private final RecruiterRepository recruiterRepository;

    public AdminRecruiterListService(RecruiterRepository recruiterRepository) {
        this.recruiterRepository = recruiterRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminRecruiterItem> listRecruiters(
            int page,
            int limit,
            String search,
            String status,
            Long companyId,
            String sortBy,
            String order) {

        // 1. Validation de la pagination
        if (page < 1 || limit < 1 || limit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paramètres de pagination invalides (page >= 1, 1 <= limit <= 100).");
        }

        // 2. Validation du statut utilisateur
        if (status != null && !status.isBlank() && !ALLOWED_USER_STATUSES.contains(status.trim().toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Statut invalide. Valeurs acceptées : ACTIVE, SUSPENDED.");
        }

        // 3. Mapping et validation du tri
        String field = sortBy != null ? sortBy.trim().toLowerCase() : "created_at";
        String entityField = switch (field) {
            case "email" -> "user.email";
            case "lastname", "last_name" -> "lastName";
            case "created_at" -> "createdAt";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Champ de tri non supporté : " + sortBy);
        };

        Sort.Direction direction = "asc".equalsIgnoreCase(order) ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageRequest = PageRequest.of(page - 1, limit, Sort.by(direction, entityField));

        // 4. Recherche
        String normalizedSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        String normalizedStatus = (status != null && !status.isBlank()) ? status.trim().toUpperCase() : null;

        Page<Recruiter> recruitersPage = recruiterRepository.findAdminRecruiters(
                normalizedSearch,
                normalizedStatus,
                companyId,
                pageRequest
        );

        // 5. Mapping vers AdminRecruiterItem
        var items = recruitersPage.getContent().stream()
                .map(r -> new AdminRecruiterItem(
                        r.getId(),
                        r.getUser().getId(),
                        r.getUser().getEmail(),
                        r.getFirstName(),
                        r.getLastName(),
                        r.getCompany() != null ? r.getCompany().getId() : null,
                        r.getCompany() != null ? r.getCompany().getName() : null,
                        r.getUser().getStatus(),
                        r.getCreatedAt()
                ))
                .toList();

        return new PageResponse<>(
            items,
            new PageResponse.Pagination(
                    page,
                    limit,
                    recruitersPage.getTotalElements(),
                    recruitersPage.getTotalPages()
            )
        );
    }
}