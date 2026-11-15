package com.gpstl.joyn.recruiter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record AdminRecruiterItem(
        Long id,                   // ID du profil recruteur
        Long userId,               // ID de l'utilisateur socle
        String email,
        String firstName,
        String lastName,
        Long companyId,
        String companyName,
        String userStatus,         // ACTIVE, SUSPENDED
        Instant createdAt,
        @JsonProperty("avatar_url") String avatarUrl
) {
    public AdminRecruiterItem(
            Long id,
            Long userId,
            String email,
            String firstName,
            String lastName,
            Long companyId,
            String companyName,
            String userStatus,
            Instant createdAt
    ) {
        this(id, userId, email, firstName, lastName, companyId, companyName, userStatus, createdAt, null);
    }
}
