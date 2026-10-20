package com.gpstl.joyn.joboffer;

import com.gpstl.joyn.joboffer.controller.*;
import com.gpstl.joyn.joboffer.exception.JobApiException;
import com.gpstl.joyn.joboffer.service.JobClosureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Vérifie le contrat HTTP du DELETE et son corps facultatif, avec un service simulé. */
class JobClosureControllerTest {
    private JobClosureService jobs;
    private MockMvc mvc;
    @BeforeEach void setup() {
        jobs = mock(JobClosureService.class);
        mvc = MockMvcBuilders.standaloneSetup(new JobClosureController(jobs))
            .setControllerAdvice(new JobExceptionHandler()).build();
    }

    @Test void deleteAcceptsOptionalReasonAndReturnsNoBody() throws Exception {
        mvc.perform(delete("/api/v1/jobs/1").principal(() -> "7"))
            .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(jobs).close(7, 1, null);
        mvc.perform(delete("/api/v1/jobs/2").principal(() -> "7").contentType("application/json")
            .content("{\"reason\":\"Poste pourvu\"}"))
            .andExpect(status().isNoContent());
        verify(jobs).close(7, 2, "Poste pourvu");
    }

    @Test void rejectsInvalidPrincipal() throws Exception {
        mvc.perform(delete("/api/v1/jobs/1").principal(() -> "someone@example.com"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(jobs);
    }
    @Test void rejectsUnauthenticatedMalformedAndInvalidRequests() throws Exception {
        mvc.perform(delete("/api/v1/jobs/1").header("X-User-Id", "7")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/jobs/0").principal(() -> "7")).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/jobs/abc").principal(() -> "7")).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/jobs/1").principal(() -> "7").contentType("application/json").content("{\"reason\":12}"))
            .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/jobs/1").principal(() -> "7").contentType("application/json").content("{\"unknown\":true}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(jobs);
    }
    @Test void exposesForbiddenAndMissingResources() throws Exception {
        doThrow(new JobApiException(403,"Forbidden")).when(jobs).close(7,1,null);
        mvc.perform(delete("/api/v1/jobs/1").principal(() -> "7")).andExpect(status().isForbidden());
        doThrow(new JobApiException(404,"Missing")).when(jobs).close(7,2,null);
        mvc.perform(delete("/api/v1/jobs/2").principal(() -> "7")).andExpect(status().isNotFound());
    }

}
