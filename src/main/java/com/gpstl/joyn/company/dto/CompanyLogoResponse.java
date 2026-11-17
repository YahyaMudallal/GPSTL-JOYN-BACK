package com.gpstl.joyn.company.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Réponse des routes du logo d'entreprise (R84).
 * L'URL est donnée sous deux noms, comme pour les photos de profil (U86) :
 * logo_url (style des routes admin) et logoUrl (style de CompanyResponse).
 * Les deux valent null quand l'entreprise n'a pas de logo.
 */
public record CompanyLogoResponse(
        @JsonProperty("company_id") long companyId,
        @JsonProperty("logo_url") String logoUrlSnake,
        @JsonProperty("logoUrl") String logoUrl) {

    public static CompanyLogoResponse of(long companyId, String url) {
        return new CompanyLogoResponse(companyId, url, url);
    }
}
