
package com.gpstl.joyn.joboffer.controller;

import com.gpstl.joyn.joboffer.exception.JobApiException;
import com.gpstl.joyn.joboffer.service.JobClosureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class JobClosureControllerTest {

    private JobClosureService jobs;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        jobs = mock(JobClosureService.class);
        mvc = MockMvcBuilders.standaloneSetup(new JobClosureController(jobs))
                .setControllerAdvice(new JobExceptionHandler())
                .build();
    }

    // Existing DELETE tests

    @Test
    void deleteAcceptsOptionalReasonAndReturnsNoBody() throws Exception {
        mvc.perform(delete("/api/v1/jobs/1").principal(() -> "7"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(jobs).close(7, 1, null);

        mvc.perform(delete("/api/v1/jobs/2")
                        .principal(() -> "7")
                        .contentType("application/json")
                        .content("{\"reason\":\"Poste pourvu\"}"))
                .andExpect(status().isNoContent());

        verify(jobs).close(7, 2, "Poste pourvu");
    }

    @Test
    void rejectsInvalidPrincipal() throws Exception {
        mvc.perform(delete("/api/v1/jobs/1")
                        .principal(() -> "someone@example.com"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(jobs);
    }

    @Test
    void rejectsUnauthenticatedMalformedAndInvalidRequests() throws Exception {
        mvc.perform(delete("/api/v1/jobs/1").header("X-User-Id", "7"))
                .andExpect(status().isUnauthorized());

        mvc.perform(delete("/api/v1/jobs/0").principal(() -> "7"))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/api/v1/jobs/abc").principal(() -> "7"))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/api/v1/jobs/1")
                        .principal(() -> "7")
                        .contentType("application/json")
                        .content("{\"reason\":12}"))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/api/v1/jobs/1")
                        .principal(() -> "7")
                        .contentType("application/json")
                        .content("{\"unknown\":true}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(jobs);
    }

    @Test
    void exposesForbiddenAndMissingResources() throws Exception {
        doThrow(new JobApiException(403, "Forbidden"))
                .when(jobs).close(7, 1, null);

        mvc.perform(delete("/api/v1/jobs/1").principal(() -> "7"))
                .andExpect(status().isForbidden());

        doThrow(new JobApiException(404, "Missing"))
                .when(jobs).close(7, 2, null);

        mvc.perform(delete("/api/v1/jobs/2").principal(() -> "7"))
                .andExpect(status().isNotFound());
    }

    // R74: Archive endpoint tests

    @Test
    void archivesFilledPositionWithHiredCandidate() throws Exception {
        mvc.perform(post("/api/v1/jobs/1/archive")
                        .principal(() -> "7")
                        .contentType("application/json")
                        .content("""
                                {
                                  "outcome": "FILLED",
                                  "hiredApplicationId": 456
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(jobs).archive(7, 1, "FILLED", 456L);
    }

    @Test
    void archivesPositionNotFilled() throws Exception {
        mvc.perform(post("/api/v1/jobs/1/archive")
                        .principal(() -> "7")
                        .contentType("application/json")
                        .content("""
                                {"outcome": "NOT_FILLED"}
                                """))
                .andExpect(status().isNoContent());

        verify(jobs).archive(7, 1, "NOT_FILLED", null);
    }

    @Test
    void archivesPositionWithRecruitmentOngoing() throws Exception {
        mvc.perform(post("/api/v1/jobs/1/archive")
                        .principal(() -> "7")
                        .contentType("application/json")
                        .content("""
                                {"outcome": "ONGOING"}
                                """))
                .andExpect(status().isNoContent());

        verify(jobs).archive(7, 1, "ONGOING", null);
    }

    @Test
    void archiveRejectsInvalidRequests() throws Exception {
        String endpoint = "/api/v1/jobs/1/archive";

        mvc.perform(post(endpoint)
                        .principal(() -> "7")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post(endpoint)
                        .principal(() -> "7")
                        .contentType("application/json")
                        .content("{\"outcome\":123}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post(endpoint)
                        .principal(() -> "7")
                        .contentType("application/json")
                        .content("{\"outcome\":\"FILLED\",\"unknown\":true}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post(endpoint)
                        .principal(() -> "7")
                        .contentType("application/json")
                        .content("{\"outcome\":\"FILLED\",\"hiredApplicationId\":-1}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post(endpoint)
                        .principal(() -> "7")
                        .contentType("application/json")
                        .content("{\"outcome\":\"FILLED\",\"hiredApplicationId\":1.5}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(jobs);
    }

    @Test
    void archiveRejectsUnauthenticatedAndInvalidIds() throws Exception {
        mvc.perform(post("/api/v1/jobs/1/archive")
                        .contentType("application/json")
                        .content("{\"outcome\":\"ONGOING\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/jobs/0/archive")
                        .principal(() -> "7")
                        .contentType("application/json")
                        .content("{\"outcome\":\"ONGOING\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(jobs);
    }

    @Test
    void archivePropagatesForbiddenAndNotFound() throws Exception {
        doThrow(new JobApiException(403, "Forbidden"))
                .when(jobs).archive(7, 1, "ONGOING", null);

        mvc.perform(post("/api/v1/jobs/1/archive")
                        .principal(() -> "7")
                        .contentType("application/json")
                        .content("{\"outcome\":\"ONGOING\"}"))
                .andExpect(status().isForbidden());

        doThrow(new JobApiException(404, "Missing"))
                .when(jobs).archive(7, 2, "ONGOING", null);

        mvc.perform(post("/api/v1/jobs/2/archive")
                        .principal(() -> "7")
                        .contentType("application/json")
                        .content("{\"outcome\":\"ONGOING\"}"))
                .andExpect(status().isNotFound());
    }
}
