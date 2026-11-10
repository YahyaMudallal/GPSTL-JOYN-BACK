package com.gpstl.joyn.candidate.controller;

import com.gpstl.joyn.candidate.dto.CandidateRegisterRequest;
import com.gpstl.joyn.candidate.dto.CandidateRegisterResponse;
import com.gpstl.joyn.candidate.dto.CandidateResumeResponse;
import com.gpstl.joyn.candidate.exception.CandidateEmailAlreadyRegisteredException;
import com.gpstl.joyn.candidate.service.CandidateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

class CandidateControllerTest {

    private CandidateService registrationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        registrationService = mock(CandidateService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new CandidateController(registrationService))
                .setControllerAdvice(new RegistrationExceptionHandler())
                .build();
    }

    @Test
    void registersCandidateAndAcceptsLegacyCvField() throws Exception {
        CandidateRegisterResponse response = new CandidateRegisterResponse(
                8L, 21L, "candidate@example.com", "Taylor", "Jordan", null);
        when(registrationService.register(any(CandidateRegisterRequest.class), any()))
                .thenReturn(response);

        MockMultipartFile cv = new MockMultipartFile(
                "cv", "resume.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[] {1, 2, 3});
        mockMvc.perform(multipart("/api/v1/candidates/register")
                        .file(cv)
                        .param("firstName", "Taylor")
                        .param("lastName", "Jordan")
                        .param("email", "candidate@example.com")
                        .param("password", "SecurePassword123!"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.profileId").value(8))
                .andExpect(jsonPath("$.userId").value(21))
                .andExpect(jsonPath("$.email").value("candidate@example.com"));

        verify(registrationService).register(
                eq(new CandidateRegisterRequest(
                        "candidate@example.com",
                        "SecurePassword123!",
                        "Taylor",
                        "Jordan")),
                eq(cv));
    }

    @Test
    void duplicateEmailReturnsConflict() throws Exception {
        when(registrationService.register(
                any(CandidateRegisterRequest.class),
                nullable(org.springframework.web.multipart.MultipartFile.class)))
                .thenThrow(new CandidateEmailAlreadyRegisteredException());

        mockMvc.perform(multipart("/api/v1/candidates/register")
                        .param("firstName", "Taylor")
                        .param("lastName", "Jordan")
                        .param("email", "candidate@example.com")
                        .param("password", "SecurePassword123!"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already registered"));
    }

    @Test
    void acceptsResumeUploadForOwnProfile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "resume", "resume.pdf", MediaType.APPLICATION_PDF_VALUE, "%PDF-1.7\nbody".getBytes());
        when(registrationService.updateResume(21L, file))
                .thenReturn(new CandidateResumeResponse("https://storage.example/resume?signature=temporary"));

        mockMvc.perform(multipart("/api/v1/candidates/profile/resume")
                        .file(file)
                        .with(request -> {
                            request.setMethod("PUT");
                            request.setUserPrincipal(() -> "21");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cv_url").value("https://storage.example/resume?signature=temporary"));

        verify(registrationService).updateResume(21L, file);
    }

    @Test
    void reportsServiceUnavailableWhenResumeStorageIsNotConfigured() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "resume", "resume.pdf", MediaType.APPLICATION_PDF_VALUE, "%PDF-1.7\nbody".getBytes());
        when(registrationService.updateResume(21L, file))
                .thenThrow(new ResponseStatusException(SERVICE_UNAVAILABLE, "Resume storage unavailable"));

        mockMvc.perform(multipart("/api/v1/candidates/profile/resume")
                        .file(file)
                        .with(request -> {
                            request.setMethod("PUT");
                            request.setUserPrincipal(() -> "21");
                            return request;
                        }))
                .andExpect(status().isServiceUnavailable());
    }
}
