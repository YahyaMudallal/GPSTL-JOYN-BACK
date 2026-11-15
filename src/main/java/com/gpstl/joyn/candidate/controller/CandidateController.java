package com.gpstl.joyn.candidate.controller;

import com.gpstl.joyn.candidate.dto.CandidateRegisterRequest;
import com.gpstl.joyn.candidate.dto.CandidateRegisterResponse;
import com.gpstl.joyn.candidate.dto.CandidateResumeResponse;
import com.gpstl.joyn.candidate.exception.CandidateProfileException;
import com.gpstl.joyn.candidate.service.CandidateService;
import com.gpstl.joyn.user.dto.ProfilePictureResponse;
import com.gpstl.joyn.user.service.UserProfilePictureService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/candidates")
@Tag(name = "Candidates", description = "Gestion complète du compte candidat, de son profil, de ses CVs et de ses candidatures")
@CrossOrigin(origins = "*")
public class CandidateController {

    private final CandidateService registrationService;
    private final UserProfilePictureService profilePictureService;

    public CandidateController(CandidateService registrationService) {
        this(registrationService, null);
    }

    public CandidateController(
            CandidateService registrationService,
            UserProfilePictureService profilePictureService) {
        this.registrationService = registrationService;
        this.profilePictureService = profilePictureService;
    }

    /**
     * Creates the candidate account and optionally stores a resume.
     * "cv" remains accepted as an alias for older clients.
     */
    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Register a new candidate account",
        description = "Creates a candidate account and optionally stores a PDF resume")
    public ResponseEntity<CandidateRegisterResponse> register(
            @RequestParam String firstName,
            @RequestParam String lastName,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam(value = "location", required = false) String location,
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
                    lastName,
                    location);
        CandidateRegisterResponse response =
                registrationService.register(
                    request,
                    uploadedResume);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping(value = "/profile/resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload or replace the authenticated candidate's resume",
            description = "Accepts a PDF up to 5 MB and returns a temporary URL for the stored file")
    public ResponseEntity<CandidateResumeResponse> updateResume(
            @RequestParam(value = "resume", required = false) MultipartFile resume,
            @RequestParam(value = "cv", required = false) MultipartFile legacyCv,
            Principal principal) {
        if (resume != null && !resume.isEmpty()
                && legacyCv != null && !legacyCv.isEmpty()) {
            throw new IllegalArgumentException("Provide either resume or cv, not both");
        }
        MultipartFile uploadedResume = resume == null || resume.isEmpty() ? legacyCv : resume;
        return ResponseEntity.ok(registrationService.updateResume(userId(principal), uploadedResume));
    }

    @PutMapping(value = {"/profile/photo", "/profile/picture"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload or replace the authenticated candidate's profile picture",
            description = "Accepts an image, center-crops to 1:1, resizes to 512x512, converts to WebP 80% and returns the URL")
    public ResponseEntity<ProfilePictureResponse> updateProfilePicture(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "photo", required = false) MultipartFile photo,
            @RequestParam(value = "picture", required = false) MultipartFile picture,
            @RequestParam(value = "image", required = false) MultipartFile image,
            Principal principal) {
        MultipartFile upload = file != null && !file.isEmpty() ? file
                : photo != null && !photo.isEmpty() ? photo
                : picture != null && !picture.isEmpty() ? picture
                : image;
        if (upload == null || upload.isEmpty()) {
            throw new IllegalArgumentException("Provide an image file (file, photo, picture, or image)");
        }
        return ResponseEntity.ok(profilePictureService.updateProfilePicture(userId(principal), upload));
    }

    @DeleteMapping(value = {"/profile/photo", "/profile/picture"})
    @Operation(summary = "Remove the authenticated candidate's profile picture")
    public ResponseEntity<Void> deleteProfilePicture(Principal principal) {
        profilePictureService.deleteProfilePicture(userId(principal));
        return ResponseEntity.noContent().build();
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
