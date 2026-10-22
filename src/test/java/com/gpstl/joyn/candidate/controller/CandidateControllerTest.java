package com.gpstl.joyn.candidate.controller;

import com.gpstl.joyn.candidate.dto.CandidateRegisterRequest;
import com.gpstl.joyn.candidate.dto.CandidateRegisterResponse;
import com.gpstl.joyn.candidate.exception.CandidateEmailAlreadyRegisteredException;
import com.gpstl.joyn.candidate.service.CandidateResumeStorage;
import com.gpstl.joyn.candidate.service.CandidateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    void servesStoredResume() throws Exception {
        byte[] resumeData = "resume".getBytes();
        CandidateResumeStorage.StoredCandidateResume resume =
                new CandidateResumeStorage.StoredCandidateResume(
                        new ByteArrayResource(resumeData), MediaType.TEXT_PLAIN);
        when(registrationService.loadResume("stored-key"))
                .thenReturn(resume);

        mockMvc.perform(get("/api/v1/candidates/resumes/stored-key"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().bytes(resumeData));
    }
}
