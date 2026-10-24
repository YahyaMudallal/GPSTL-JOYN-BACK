package com.gpstl.joyn.candidate.exception;

public class CandidateResumeNotFoundException extends RuntimeException {

    public CandidateResumeNotFoundException() {
        super("Candidate resume not found");
    }
}
