package com.gpstl.joyn.candidate.controller;

import com.gpstl.joyn.candidate.service.CandidateResumeStorage;
import com.gpstl.joyn.candidate.service.CandidateService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LocalCandidateResumeControllerTest {

    @Test
    void servesResumeOnlyThroughTheLocalAdapter() throws Exception {
        CandidateService candidateService = mock(CandidateService.class);
        byte[] content = "%PDF-1.7".getBytes();
        when(candidateService.loadResume(42L, 42L, "resume.pdf"))
                .thenReturn(new CandidateResumeStorage.StoredCandidateResume(
                        new ByteArrayResource(content), MediaType.APPLICATION_PDF));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                        new LocalCandidateResumeController(candidateService))
                .build();

        mockMvc.perform(get("/api/v1/candidates/resumes/42/resume.pdf")
                        .principal(() -> "42"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(content));
    }
}
