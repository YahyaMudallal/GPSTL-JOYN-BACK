package com.gpstl.joyn.resume.controller;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.gpstl.joyn.resume.dto.ResumeParseResponse;
import com.gpstl.joyn.resume.service.ResumeService;

@RestController
@RequestMapping("/api/v1/resumes")
public class ResumeController {
    
    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService){
        this.resumeService = resumeService;
    }

    /**
     * Extracts information from a PDF resume to prefill the candidate registration form.
     */
    @PostMapping(
        value = "/parse",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> parseResume(
        @RequestParam("file") MultipartFile file
    ){
        try {
            ResumeParseResponse parsedData = resumeService.parseResume(file);
            return ResponseEntity.ok(parsedData);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

}
