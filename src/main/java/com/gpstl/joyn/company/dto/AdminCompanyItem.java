package com.gpstl.joyn.company.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gpstl.joyn.company.model.Company;

import java.time.Instant;

/**
 * Une entreprise dans la liste du back-office admin (A66).
 * On ne renvoie que ce qui est utile à la modération : pas la description complète ni l'adresse.
 */
public record AdminCompanyItem(
        Long id,
        String name,
        String siret,
        @JsonProperty("siret_verified") boolean siretVerified,
        @JsonProperty("email_domain") String emailDomain,
        String sector,
        @JsonProperty("company_size") String companySize,
        @JsonProperty("verification_status") String verificationStatus,
        @JsonProperty("logo_url") String logoUrl,
        String website,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt
) {

    public static AdminCompanyItem from(Company company) {
        return new AdminCompanyItem(
                company.getId(),
                company.getName(),
                company.getSiret(),
                company.isCheckSiretFound(),
                company.getEmailDomain(),
                company.getSector(),
                company.getCompanySize(),
                company.getVerificationStatus(),
                company.getLogoUrl(),
                company.getWebsite(),
                company.getCreatedAt(),
                company.getUpdatedAt());
    }
}
