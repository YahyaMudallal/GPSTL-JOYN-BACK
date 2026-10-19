package com.gpstl.joyn.candidate.dto;

public record CandidateRegisterResponse(
        Long profileId,
        Long userId,
        String email,
        String firstName,
        String lastName,
        String resumeUrl) {
}
