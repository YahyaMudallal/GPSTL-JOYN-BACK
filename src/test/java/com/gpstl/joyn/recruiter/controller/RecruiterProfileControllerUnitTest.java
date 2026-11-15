package com.gpstl.joyn.recruiter.controller;

import com.gpstl.joyn.recruiter.service.RecruiterProfileService;
import com.gpstl.joyn.user.dto.ProfilePictureResponse;
import com.gpstl.joyn.user.service.UserProfilePictureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RecruiterProfileControllerUnitTest {

    private MockMvc mockMvc;

    @Mock
    private RecruiterProfileService profiles;

    @Mock
    private UserProfilePictureService profilePictureService;

    private Principal principal;

    @BeforeEach
    void setUp() {
        RecruiterProfileController controller = new RecruiterProfileController(profiles, profilePictureService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new RecruiterProfileExceptionHandler())
                .build();
        principal = () -> "55";
    }

    @Test
    @DisplayName("PUT /api/v1/recruiters/profile/photo - 200 OK with uploaded file")
    void shouldUploadPhotoForRecruiter() throws Exception {
        MockMultipartFile file = new MockMultipartFile("photo", "recruiter.png", "image/png", new byte[]{1, 2, 3});
        when(profilePictureService.updateProfilePicture(eq(55L), any()))
                .thenReturn(new ProfilePictureResponse("https://storage.test/recruiter.webp", "https://storage.test/recruiter.webp"));

        mockMvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/v1/recruiters/profile/photo")
                        .file(file)
                        .principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photo_url").value("https://storage.test/recruiter.webp"));

        verify(profilePictureService).updateProfilePicture(eq(55L), any());
    }

    @Test
    @DisplayName("DELETE /api/v1/recruiters/profile/photo - 204 No Content")
    void shouldDeletePhotoForRecruiter() throws Exception {
        mockMvc.perform(delete("/api/v1/recruiters/profile/photo").principal(principal))
                .andExpect(status().isNoContent());

        verify(profilePictureService).deleteProfilePicture(55L);
    }
}
