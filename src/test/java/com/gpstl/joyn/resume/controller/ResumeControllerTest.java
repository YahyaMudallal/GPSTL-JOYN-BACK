package com.gpstl.joyn.resume.controller;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import com.gpstl.joyn.resume.dto.ResumeParseResponse;
import com.gpstl.joyn.resume.dto.ResumeParseResponse.ContactInfo;
import com.gpstl.joyn.resume.dto.ResumeParseResponse.Education;
import com.gpstl.joyn.resume.dto.ResumeParseResponse.Experience;
import com.gpstl.joyn.resume.parser.ResumeParsingException;
import com.gpstl.joyn.resume.service.InvalidResumeException;
import com.gpstl.joyn.resume.service.ResumeService;

class ResumeControllerTest {

    private ResumeService resumeService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        resumeService = mock(ResumeService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new ResumeController(resumeService))
            .build();
    }

    @Test
    void returnsParsedResumeAsJson() throws Exception {
        ResumeParseResponse parsedResume = new ResumeParseResponse(
            new ContactInfo(
                "Ada",
                "Lovelace",
                "ada@example.com",
                "+441234567890",
                "London",
                "United Kingdom"
            ),
            List.of(new Education(
                "Mathematics",
                "University of London",
                "1830-01",
                "1835-01"
            )),
            List.of(new Experience(
                "Mathematician",
                "Analytical Engine",
                "1842-01",
                "1852-01"
            )),
            List.of("Mathematics", "Computing")
        );
        MockMultipartFile file = pdfUpload();
        when(resumeService.parseResume(any(MultipartFile.class)))
            .thenReturn(parsedResume);

        mockMvc.perform(multipart("/api/v1/resumes/parse").file(file))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.contactInfo.firstName").value("Ada"))
            .andExpect(jsonPath("$.contactInfo.email").value("ada@example.com"))
            .andExpect(jsonPath("$.education[0].degree").value("Mathematics"))
            .andExpect(jsonPath("$.experiences[0].company").value("Analytical Engine"))
            .andExpect(jsonPath("$.skills[0]").value("Mathematics"))
            .andExpect(jsonPath("$.skills[1]").value("Computing"));

        verify(resumeService).parseResume(any(MultipartFile.class));
    }

    @Test
    void invalidResumeReturnsBadRequest() throws Exception {
        when(resumeService.parseResume(any(MultipartFile.class)))
            .thenThrow(new InvalidResumeException(
                "The uploaded file must be a PDF"
            ));

        mockMvc.perform(multipart("/api/v1/resumes/parse")
                .file(new MockMultipartFile("file", "resume.txt",
                    "text/plain", "not a pdf".getBytes())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value(
                "The uploaded file must be a PDF"
            ));
    }

    @Test
    void resumeParsingFailureReturnsBadGateway() throws Exception {
        when(resumeService.parseResume(any(MultipartFile.class)))
            .thenThrow(new ResumeParsingException("Gemini is unavailable"));

        mockMvc.perform(multipart("/api/v1/resumes/parse")
                .file(pdfUpload()))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.error").value(
                "Gemini is unavailable"
            ));
        verify(resumeService).parseResume(any(MultipartFile.class));
    }

    @Test
    void missingFileReturnsBadRequestWithoutCallingService() throws Exception {
        mockMvc.perform(multipart("/api/v1/resumes/parse"))
            .andExpect(status().isBadRequest());

        verify(resumeService, never()).parseResume(any(MultipartFile.class));
    }

    /**
     * Creates a mock PDF file for testing purposes.
     * @return A mock PDF file
     */
    private MockMultipartFile pdfUpload() {
        return new MockMultipartFile(
            "file",
            "resume.pdf",
            MediaType.APPLICATION_PDF_VALUE,
            "%PDF-1.7".getBytes()
        );
    }

}
