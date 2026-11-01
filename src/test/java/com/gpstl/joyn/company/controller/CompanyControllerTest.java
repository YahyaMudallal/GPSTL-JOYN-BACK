package com.gpstl.joyn.company.controller;

import java.util.Map;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import com.gpstl.joyn.company.dto.CompanyResponse;
import com.gpstl.joyn.company.service.CompanyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class CompanyControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CompanyService companyService;

    @InjectMocks
    private CompanyController companyController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(companyController).build();
    }

    @Test
    @DisplayName("GET /api/v1/companies/{id} - 200 OK avec headers Cache-Control et ETag")
    void getCompany_Success() throws Exception {
        CompanyResponse mockResponse = new CompanyResponse(
                1L,
                "TechCorp",
                "https://logo.png",
                "IT",
                "50-100",
                "https://techcorp.com",
                "Description",
                "Paris",
                0,
                List.of()
        );

        when(companyService.getProfile(eq(1L), eq(true), any())).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/companies/1"))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(header().string("Cache-Control", "max-age=300, public"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("TechCorp"));
    }

    @Test
    @DisplayName("GET /api/v1/companies/{id} - 404 quand l'entreprise n'existe pas ou non accessible")
    void getCompany_NotFound() throws Exception {
        when(companyService.getProfile(eq(99L), eq(true), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/v1/companies/99"))
                .andExpect(status().isNotFound());
    }


    @Test
    @DisplayName("PATCH /api/v1/companies/{id} - 200 OK")
    void updateCompany_Success() throws Exception {
        CompanyResponse mockResponse = new CompanyResponse(
                1L,
                "TechCorp",
                "https://logo.png",
                "IT",
                "50-100",
                "https://techcorp.com",
                "Nouvelle description",
                "Paris",
                0,
                List.of()
        );

        when(companyService.updateProfile(eq(1L), any(), any()))
                .thenReturn(mockResponse);

        mockMvc.perform(patch("/api/v1/companies/1")
                        .contentType("application/json")
                        .content("{\"description\":\"Nouvelle description\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Nouvelle description"));

        verify(companyService).updateProfile(
                eq(1L),
                eq(Map.of("description", "Nouvelle description")),
                any()
        );
    }
}
