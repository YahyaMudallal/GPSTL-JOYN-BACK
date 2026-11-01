package com.gpstl.joyn.candidate.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gpstl.joyn.candidate.model.Candidate;
import com.gpstl.joyn.common.model.User;

import java.time.Instant;

/**
 * Un candidat dans la liste du back-office admin (A80).
 * Champs choisis un par un : le hash du mot de passe et les jetons ne peuvent pas y apparaître.
 *
 * @param id          users.id
 * @param candidateId candidate_profiles.id, à utiliser pour GET /api/v1/candidates/{id}/profile
 */
public record AdminCandidateItem(
        Long id,
        @JsonProperty("candidate_id") Long candidateId,
        String email,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        String role,
        String status,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("is_email_verified") boolean emailVerified,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("last_login_at") Instant lastLoginAt
) {

    public static AdminCandidateItem from(Candidate candidate) {
        User user = candidate.getUser();
        return new AdminCandidateItem(
                user.getId(),
                candidate.getId(),
                user.getEmail(),
                candidate.getFirstName(),
                candidate.getLastName(),
                user.getRole(),
                user.getStatus(),
                candidate.getPhotoUrl(),
                user.getEmailVerifiedAt() != null,
                user.getCreatedAt(),
                user.getLastLoginAt());
    }
}
