package com.gpstl.joyn.candidate.service;

public class CandidateEmailAlreadyRegisteredException extends IllegalArgumentException {

    public CandidateEmailAlreadyRegisteredException() {
        super("Email already registered");
    }
}
