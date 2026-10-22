package com.gpstl.joyn.candidate.exception;

public class CandidateEmailAlreadyRegisteredException extends IllegalArgumentException {

    public CandidateEmailAlreadyRegisteredException() {
        super("Email already registered");
    }
}
