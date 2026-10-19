package com.gpstl.joyn.resume.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.gpstl.joyn.resume.dto.ResumeParseResponse;
import com.gpstl.joyn.resume.parser.ResumeParsingException;
import com.gpstl.joyn.resume.service.InvalidResumeException;
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
        } catch (InvalidResumeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (ResumeParsingException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                Map.of("error", e.getMessage())
            );
        }
    }

}