package com.gpstl.joyn.candidate.controller;

import com.gpstl.joyn.candidate.service.CandidateEmailAlreadyRegisteredException;
import com.gpstl.joyn.candidate.service.CandidateResumeNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class RegistrationExceptionHandler {

    @ExceptionHandler(CandidateEmailAlreadyRegisteredException.class)
    public ResponseEntity<Map<String, String>> emailAlreadyRegistered(
            CandidateEmailAlreadyRegisteredException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalidRequest(
            IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(CandidateResumeNotFoundException.class)
    public ResponseEntity<Map<String, String>> resumeNotFound(
            CandidateResumeNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", exception.getMessage()));
    }
}
