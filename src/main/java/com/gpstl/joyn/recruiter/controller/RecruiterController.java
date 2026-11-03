package com.gpstl.joyn.recruiter.controller;

import com.gpstl.joyn.recruiter.dto.RecruiterRegisterRequest;
import com.gpstl.joyn.recruiter.dto.RecruiterRegisterResponse;
import com.gpstl.joyn.recruiter.service.RecruiterService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/recruiters")
@Tag(name = "Recruiters", description = "Permet aux recruteurs de s'enregistrer et de gérer les informations de leur profil")
@CrossOrigin(origins = "*") // Permet les appels depuis Flutter en dev local
public class RecruiterController {

    private final RecruiterService recruiterService;

    // Injection de dépendance du service
    public RecruiterController(RecruiterService recruiterService) {
        this.recruiterService = recruiterService;
    }

    /**
     * Endpoint d'inscription du recruteur (User Story R55)
     * URL : POST http://localhost:8080/api/v1/recruiters/register
     */
    @PostMapping("/register")
    @Operation(
        summary = "Register a new recruiter account",
        description = "Creates a recruiter user, links them to a specified company, and initializes their recruiter profile")
    public ResponseEntity<?> register(@RequestBody RecruiterRegisterRequest request) {
        try {
            RecruiterRegisterResponse response = recruiterService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            // Renvoie une erreur 409 (Conflict) si l'email existe déjà, par exemple
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        }
    }
}