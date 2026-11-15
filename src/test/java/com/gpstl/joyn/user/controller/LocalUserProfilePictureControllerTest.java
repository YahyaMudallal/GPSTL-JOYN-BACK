package com.gpstl.joyn.user.controller;

import com.gpstl.joyn.user.service.UserProfilePictureStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LocalUserProfilePictureControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserProfilePictureStorage storage;

    @BeforeEach
    void setUp() {
        LocalUserProfilePictureController controller = new LocalUserProfilePictureController(storage);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/v1/users/profile-pictures/{userId}/{fileName} - 200 OK with webp content")
    void shouldServeProfilePictureLocally() throws Exception {
        byte[] data = "RIFF....WEBP".getBytes();
        var stored = new UserProfilePictureStorage.StoredProfilePicture(
                new ByteArrayResource(data),
                MediaType.parseMediaType("image/webp")
        );

        when(storage.load("profile_pictures/42/avatar.webp")).thenReturn(Optional.of(stored));

        mockMvc.perform(get("/api/v1/users/profile-pictures/42/avatar.webp"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/webp"))
                .andExpect(header().string("Cache-Control", "public, max-age=86400"))
                .andExpect(content().bytes(data));
    }

    @Test
    @DisplayName("GET /api/v1/users/profile-pictures/{userId}/{fileName} - 404 Not Found if missing")
    void shouldReturn404WhenNotFound() throws Exception {
        when(storage.load("profile_pictures/42/missing.webp")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/users/profile-pictures/42/missing.webp"))
                .andExpect(status().isNotFound());
    }
}
