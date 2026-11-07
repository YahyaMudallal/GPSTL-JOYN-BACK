package com.gpstl.joyn.candidate.dto;

public record CandidateRegisterRequest(
        String email,
        String password,
        String firstName,
        String lastName,
        String location) {

    public CandidateRegisterRequest(
        String email, 
        String password, 
        String firstName, 
        String lastName) {
        this(
            email, 
            password, 
            firstName, 
            lastName, 
            null);
    }
}
