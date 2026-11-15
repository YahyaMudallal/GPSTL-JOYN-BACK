package com.gpstl.joyn.user.controller;

import com.gpstl.joyn.user.dto.ProfilePictureResponse;
import com.gpstl.joyn.user.exception.UserProfilePictureException;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserProfilePictureControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserProfilePictureService service;

    private Principal principal;

    @BeforeEach
    void setUp() {
        UserProfilePictureController controller = new UserProfilePictureController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new UserProfilePictureExceptionHandler())
                .build();
        principal = () -> "42";
    }

    @Test
    @DisplayName("GET /api/v1/users/profile-picture - 200 OK")
    void shouldGetProfilePicture() throws Exception {
        when(service.getProfilePicture(42L))
                .thenReturn(new ProfilePictureResponse("https://storage.test/pic.webp", "https://storage.test/pic.webp"));

        mockMvc.perform(get("/api/v1/users/profile-picture").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile_picture_url").value("https://storage.test/pic.webp"))
                .andExpect(jsonPath("$.photo_url").value("https://storage.test/pic.webp"));
    }

    @Test
    @DisplayName("PUT /api/v1/users/profile-picture - 200 OK with uploaded file")
    void shouldUploadProfilePictureViaPut() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{1, 2, 3});
        when(service.updateProfilePicture(eq(42L), any()))
                .thenReturn(new ProfilePictureResponse("https://storage.test/new.webp", "https://storage.test/new.webp"));

        mockMvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/v1/users/profile-picture")
                        .file(file)
                        .principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile_picture_url").value("https://storage.test/new.webp"))
                .andExpect(jsonPath("$.photo_url").value("https://storage.test/new.webp"));

        verify(service).updateProfilePicture(eq(42L), any());
    }

    @Test
    @DisplayName("POST /api/v1/users/profile-picture - 200 OK alias with photo parameter")
    void shouldUploadProfilePictureViaPostAlias() throws Exception {
        MockMultipartFile file = new MockMultipartFile("photo", "avatar.jpg", "image/jpeg", new byte[]{4, 5, 6});
        when(service.updateProfilePicture(eq(42L), any()))
                .thenReturn(new ProfilePictureResponse("https://storage.test/new.webp", "https://storage.test/new.webp"));

        mockMvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.POST, "/api/v1/users/profile-picture")
                        .file(file)
                        .principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile_picture_url").value("https://storage.test/new.webp"));
    }

    @Test
    @DisplayName("DELETE /api/v1/users/profile-picture - 204 No Content")
    void shouldDeleteProfilePicture() throws Exception {
        mockMvc.perform(delete("/api/v1/users/profile-picture").principal(principal))
                .andExpect(status().isNoContent());

        verify(service).deleteProfilePicture(42L);
    }

    @Test
    @DisplayName("PUT /api/v1/users/profile-picture - 400 Bad Request if no file parameter provided")
    void shouldReturn400IfNoFileProvided() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/v1/users/profile-picture")
                        .principal(principal))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("PUT /api/v1/users/profile-picture - 400 Bad Request when service throws UserProfilePictureException(400)")
    void shouldReturn400WhenServiceRejectsImage() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "corrupt.png", "image/png", new byte[]{0});
        when(service.updateProfilePicture(eq(42L), any()))
                .thenThrow(new UserProfilePictureException(400, "Image non valide"));

        mockMvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/v1/users/profile-picture")
                        .file(file)
                        .principal(principal))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Image non valide"));
    }
}
