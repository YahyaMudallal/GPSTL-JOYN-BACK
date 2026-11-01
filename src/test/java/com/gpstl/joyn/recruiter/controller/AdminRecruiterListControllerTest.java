package com.gpstl.joyn.recruiter.controller;

import com.gpstl.joyn.common.dto.PageResponse;
import com.gpstl.joyn.recruiter.dto.AdminRecruiterItem;
import com.gpstl.joyn.recruiter.service.AdminRecruiterListService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminRecruiterListControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AdminRecruiterListService adminRecruiterListService;

    @InjectMocks
    private AdminRecruiterListController adminRecruiterListController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminRecruiterListController).build();
    }

    @Test
    @DisplayName("GET /api/v1/admin/recruiters - 200 OK avec payload paginé")
    void listRecruiters_Success() throws Exception {
        AdminRecruiterItem item = new AdminRecruiterItem(
                10L,
                1L,
                "recruiter@tech.com",
                "Alice",
                "Dupont",
                20L,
                "TechCorp",
                "ACTIVE",
                Instant.now()
        );

        PageResponse<AdminRecruiterItem> mockResponse = new PageResponse<>(
                List.of(item),
                new PageResponse.Pagination(1, 20, 1L, 1)
        );

        when(adminRecruiterListService.listRecruiters(
                eq(1), eq(20), isNull(), isNull(), isNull(), eq("created_at"), eq("desc")
        )).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/admin/recruiters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(10))
                .andExpect(jsonPath("$.data[0].email").value("recruiter@tech.com"))
                .andExpect(jsonPath("$.data[0].companyName").value("TechCorp"))
                .andExpect(jsonPath("$.pagination.current_page").value(1))
                .andExpect(jsonPath("$.pagination.limit").value(20))
                .andExpect(jsonPath("$.pagination.total_items").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/admin/recruiters - 400 Bad Request si pagination invalide")
    void listRecruiters_InvalidPage_Returns400() throws Exception {
        when(adminRecruiterListService.listRecruiters(
                eq(0), anyInt(), any(), any(), any(), any(), any()
        )).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page invalide"));

        mockMvc.perform(get("/api/v1/admin/recruiters?page=0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/admin/recruiters - 400 Bad Request si statut invalide")
    void listRecruiters_InvalidStatus_Returns400() throws Exception {
        when(adminRecruiterListService.listRecruiters(
                anyInt(), anyInt(), any(), eq("INCONNU"), any(), any(), any()
        )).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Statut invalide"));

        mockMvc.perform(get("/api/v1/admin/recruiters?status=INCONNU"))
                .andExpect(status().isBadRequest());
    }
}