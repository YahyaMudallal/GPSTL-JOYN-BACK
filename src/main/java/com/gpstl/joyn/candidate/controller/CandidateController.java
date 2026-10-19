package com.gpstl.joyn.candidate.controller;

import com.gpstl.joyn.candidate.dto.CandidateRegisterRequest;
import com.gpstl.joyn.candidate.dto.CandidateRegisterResponse;
import com.gpstl.joyn.candidate.service.CandidateResumeStorage;
import com.gpstl.joyn.candidate.service.RegistrationService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/candidates")
@CrossOrigin(origins = "*")
public class CandidateController {

    private final RegistrationService registrationService;

    public CandidateController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    /**
     * Creates the candidate account and optionally stores a resume.
     * "cv" remains accepted as an alias for older clients; neither field is parsed.
     */
    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CandidateRegisterResponse> register(
            @RequestParam String firstName,
            @RequestParam String lastName,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam(value = "resume", required = false) MultipartFile resume,
            @RequestParam(value = "cv", required = false) MultipartFile legacyCv) {
        if (resume != null && !resume.isEmpty()
                && legacyCv != null && !legacyCv.isEmpty()) {
            throw new IllegalArgumentException("Provide either resume or cv, not both");
        }

        MultipartFile uploadedResume = resume == null || resume.isEmpty()
                ? legacyCv
                : resume;

        CandidateRegisterRequest request =
                new CandidateRegisterRequest(
                    email,
                    password,
                    firstName,
                    lastName);
        CandidateRegisterResponse response =
                registrationService.register(
                    request,
                    uploadedResume);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Serves locally stored resumes until a media-server adapter is configured.
     */
    @GetMapping("/resumes/{storageKey}")
    public ResponseEntity<Resource> getResume(
            @PathVariable String storageKey) {
        CandidateResumeStorage.StoredCandidateResume resume =
                registrationService.loadResume(storageKey);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(resume.mediaType());
        headers.setContentDisposition(ContentDisposition.inline().filename("resume").build());
        return ResponseEntity.ok().headers(headers).body(resume.resource());
    }
}
