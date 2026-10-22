package com.gpstl.joyn.candidate.service;

public class CandidateResumeNotFoundException extends RuntimeException {

    public CandidateResumeNotFoundException() {
        super("Candidate resume not found");
    }
}
