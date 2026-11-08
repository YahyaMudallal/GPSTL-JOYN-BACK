package com.gpstl.joyn.company.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Informations publiques ; les données absentes ne sont jamais déduites du nom. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record SiretLookupResponse(
        String siret,
        String siren,
        String legalName,
        String displayName,
        String city,
        String address,
        String postalCode,
        String activityCode,
        String activity,
        String emailDomain,
        String sector,
        String sizeRange,
        String employeeRangeCode,
        String employeeRangeYear,
        Boolean companyActive,
        Boolean establishmentActive) {
}
