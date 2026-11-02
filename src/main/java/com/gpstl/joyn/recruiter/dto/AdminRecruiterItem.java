package com.gpstl.joyn.recruiter.dto;

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
        Instant createdAt
) {}