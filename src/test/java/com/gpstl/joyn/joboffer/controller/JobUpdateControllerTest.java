package com.gpstl.joyn.joboffer.controller;

import com.gpstl.joyn.joboffer.exception.JobApiException;
import com.gpstl.joyn.joboffer.service.JobUpdateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Vérifie les entrées et réponses HTTP du PATCH avec un service simulé. */
class JobUpdateControllerTest {
    private JobUpdateService jobs;
    private MockMvc mvc;
    @BeforeEach void setup() {
        jobs = mock(JobUpdateService.class);
        mvc = MockMvcBuilders.standaloneSetup(new JobUpdateController(jobs))
            .setControllerAdvice(new JobExceptionHandler()).build();
    }

    @Test void returnsUpdatedResource() throws Exception {
        when(jobs.patch(7L, 1L, Map.of("title", "Engineer"))).thenReturn(Map.of("id", 1, "title", "Engineer"));
        mvc.perform(patch("/api/v1/jobs/1").principal(() -> "7")
            .contentType("application/json").content("{\"title\":\"Engineer\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Engineer"));
    }
    @Test void rejectsUnauthenticatedMalformedAndInvalidRequests() throws Exception {
        mvc.perform(patch("/api/v1/jobs/1").header("X-User-Id", "7").contentType("application/json").content("{}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/jobs/1").principal(() -> "invalid").contentType("application/json").content("{}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/jobs/0").principal(() -> "7").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/jobs/abc").principal(() -> "7").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/jobs/1").principal(() -> "7").contentType("application/json").content("[]"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(jobs);
    }
    @Test void exposesDetailedValidationErrors() throws Exception {
        when(jobs.patch(eq(7L),eq(1L),anyMap())).thenThrow(new JobApiException(400,"Invalid salary", Map.of("salary_max","Too small")));
        mvc.perform(patch("/api/v1/jobs/1").principal(() -> "7").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.salary_max").value("Too small"));
    }

}
