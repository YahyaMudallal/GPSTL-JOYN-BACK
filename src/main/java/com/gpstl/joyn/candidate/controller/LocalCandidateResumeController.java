package com.gpstl.joyn.candidate.controller;

import com.gpstl.joyn.candidate.exception.CandidateProfileException;
import com.gpstl.joyn.candidate.service.CandidateResumeStorage;
import com.gpstl.joyn.candidate.service.CandidateService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/candidates/resumes")
@ConditionalOnProperty(name = "joyn.storage.provider", havingValue = "local")
public class LocalCandidateResumeController {

    private final CandidateService candidateService;

    public LocalCandidateResumeController(CandidateService candidateService) {
        this.candidateService = candidateService;
    }

    @GetMapping("/{candidateId}/{fileName}")
    public ResponseEntity<Resource> getResume(
            @PathVariable Long candidateId,
            @PathVariable String fileName,
            Principal principal) {
        CandidateResumeStorage.StoredCandidateResume resume =
                candidateService.loadResume(userId(principal), candidateId, fileName);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(resume.mediaType());
        headers.setContentDisposition(ContentDisposition.inline().filename("resume.pdf").build());
        return ResponseEntity.ok().headers(headers).body(resume.resource());
    }

    private static long userId(Principal principal) {
        if (principal == null) {
            throw new CandidateProfileException(401, "Authentication is required");
        }
        try {
            long id = Long.parseLong(principal.getName());
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new CandidateProfileException(401, "Authentication must provide the database user ID");
        }
    }
}
