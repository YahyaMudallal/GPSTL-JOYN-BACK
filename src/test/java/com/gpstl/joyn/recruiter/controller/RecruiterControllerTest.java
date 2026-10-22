package com.gpstl.joyn.recruiter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gpstl.joyn.recruiter.dto.RecruiterRegisterRequest;
import com.gpstl.joyn.recruiter.dto.RecruiterRegisterResponse;
import com.gpstl.joyn.recruiter.service.RecruiterService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class RecruiterControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private RecruiterService recruiterService;

    @InjectMocks
    private RecruiterController recruiterController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(recruiterController).build();
    }

    @Test
    @DisplayName("POST /api/recruiters/register - Succès 201 avec retour du profil créé")
    void register_Success() throws Exception {
        RecruiterRegisterRequest request = new RecruiterRegisterRequest(
                "recruiter@entreprise.com",
                "Password123!",
                "Sophie",
                "Martin",
                "TechCorp"
        );

        RecruiterRegisterResponse mockResponse = new RecruiterRegisterResponse(
                10L,
                1L,
                "recruiter@entreprise.com",
                "Sophie",
                "Martin",
                "TechCorp"
        );

        when(recruiterService.register(any(RecruiterRegisterRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/recruiters/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.profileId").value(10))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.email").value("recruiter@entreprise.com"))
                .andExpect(jsonPath("$.firstName").value("Sophie"))
                .andExpect(jsonPath("$.lastName").value("Martin"))
                .andExpect(jsonPath("$.companyName").value("TechCorp"));
    }

    @Test
    @DisplayName("POST /api/recruiters/register - Échec 409 quand l'email existe déjà")
    void register_EmailAlreadyExists_Returns409() throws Exception {
        RecruiterRegisterRequest request = new RecruiterRegisterRequest(
                "duplicate@entreprise.com",
                "Password123!",
                "Jean",
                "Dupont",
                "TechCorp"
        );

        when(recruiterService.register(any(RecruiterRegisterRequest.class)))
                .thenThrow(new IllegalArgumentException("Cet email est déjà associé à un compte."));

        mockMvc.perform(post("/api/recruiters/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cet email est déjà associé à un compte."));
    }
}