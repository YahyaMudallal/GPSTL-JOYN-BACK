package com.gpstl.joyn.candidate.dto;

import java.time.LocalDate;

/**
 * Filtres facultatifs de la liste admin des candidats (A80) : null signifie « ne pas filtrer ».
 * Les dates sont interprétées en UTC ; createdFrom et createdTo sont inclus.
 */
public record AdminCandidateFilters(
        String search,
        String status,
        Boolean emailVerified,
        LocalDate createdFrom,
        LocalDate createdTo,
        LocalDate lastLoginBefore,
        String location,
        Boolean visibleToRecruiters,
        Boolean hasResume
) {
}
