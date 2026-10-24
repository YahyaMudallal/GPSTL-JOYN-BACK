package com.gpstl.joyn.candidate.dto;

public record CandidateRegisterRequest(
        String email,
        String password,
        String firstName,
        String lastName) {
}
