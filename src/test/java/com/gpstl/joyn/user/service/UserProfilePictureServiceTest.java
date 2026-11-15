package com.gpstl.joyn.user.service;

import com.gpstl.joyn.candidate.model.Candidate;
import com.gpstl.joyn.candidate.repository.CandidateRepository;
import com.gpstl.joyn.common.model.User;
import com.gpstl.joyn.common.repository.UserRepository;
import com.gpstl.joyn.user.dto.ProfilePictureResponse;
import com.gpstl.joyn.user.exception.UserProfilePictureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserProfilePictureServiceTest {

    private UserRepository userRepository;
    private CandidateRepository candidateRepository;
    private ProfilePictureImageProcessor imageProcessor;
    private UserProfilePictureStorage storage;
    private UserProfilePictureService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        candidateRepository = mock(CandidateRepository.class);
        imageProcessor = mock(ProfilePictureImageProcessor.class);
        storage = mock(UserProfilePictureStorage.class);
        service = new UserProfilePictureService(userRepository, candidateRepository, imageProcessor, storage);
    }

    @Test
    @DisplayName("Should update profile picture for candidate, delete old file and sync candidate entity")
    void shouldUpdateProfilePictureForCandidate() {
        long userId = 10L;
        User user = new User("candidate@joyn.fr", "hash", "CANDIDATE");
        user.setProfilePictureUrl("profile_pictures/10/old-uuid.webp");

        Candidate candidate = new Candidate(user, "Jane", "Doe", null);
        candidate.setPhotoUrl("profile_pictures/10/old-uuid.webp");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(candidateRepository.findByUserId(userId)).thenReturn(Optional.of(candidate));

        MockMultipartFile file = new MockMultipartFile("file", "pic.png", "image/png", new byte[]{1, 2, 3});
        byte[] processedWebp = new byte[]{10, 20, 30};
        when(imageProcessor.processProfilePicture(file)).thenReturn(processedWebp);

        String newKey = "profile_pictures/10/new-uuid.webp";
        when(storage.store(eq(userId), eq(processedWebp))).thenReturn(newKey);
        when(storage.createPresignedUrl(newKey)).thenReturn("https://storage.example.test/signed-new.webp");

        ProfilePictureResponse response = service.updateProfilePicture(userId, file);

        assertThat(response.profilePictureUrl()).isEqualTo("https://storage.example.test/signed-new.webp");
        assertThat(response.photoUrl()).isEqualTo("https://storage.example.test/signed-new.webp");

        // Old key should be deleted
        verify(storage).delete("profile_pictures/10/old-uuid.webp");

        // User & candidate entities should be updated with new key
        assertThat(user.getProfilePictureUrl()).isEqualTo(newKey);
        assertThat(candidate.getPhotoUrl()).isEqualTo(newKey);
        verify(userRepository).save(user);
        verify(candidateRepository).save(candidate);
    }

    @Test
    @DisplayName("Should update profile picture for recruiter")
    void shouldUpdateProfilePictureForRecruiter() {
        long userId = 20L;
        User user = new User("recruiter@acme.com", "hash", "RECRUITER");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(candidateRepository.findByUserId(userId)).thenReturn(Optional.empty());

        MockMultipartFile file = new MockMultipartFile("photo", "recruiter.jpg", "image/jpeg", new byte[]{4, 5, 6});
        byte[] processedWebp = new byte[]{40, 50, 60};
        when(imageProcessor.processProfilePicture(file)).thenReturn(processedWebp);

        String newKey = "profile_pictures/20/recruiter-uuid.webp";
        when(storage.store(eq(userId), eq(processedWebp))).thenReturn(newKey);
        when(storage.createPresignedUrl(newKey)).thenReturn("https://storage.example.test/recruiter.webp");

        ProfilePictureResponse response = service.updateProfilePicture(userId, file);

        assertThat(response.profilePictureUrl()).isEqualTo("https://storage.example.test/recruiter.webp");
        assertThat(user.getProfilePictureUrl()).isEqualTo(newKey);
        verify(userRepository).save(user);
        verify(candidateRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should delete profile picture and clear fields on user and candidate")
    void shouldDeleteProfilePicture() {
        long userId = 30L;
        User user = new User("user@joyn.fr", "hash", "CANDIDATE");
        user.setProfilePictureUrl("profile_pictures/30/to-delete.webp");

        Candidate candidate = new Candidate(user, "Alex", "Smith", null);
        candidate.setPhotoUrl("profile_pictures/30/to-delete.webp");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(candidateRepository.findByUserId(userId)).thenReturn(Optional.of(candidate));

        service.deleteProfilePicture(userId);

        assertThat(user.getProfilePictureUrl()).isNull();
        assertThat(candidate.getPhotoUrl()).isNull();
        verify(storage).delete("profile_pictures/30/to-delete.webp");
        verify(userRepository).save(user);
        verify(candidateRepository).save(candidate);
    }

    @Test
    @DisplayName("Should get profile picture URL")
    void shouldGetProfilePicture() {
        long userId = 40L;
        User user = new User("active@joyn.fr", "hash", "CANDIDATE");
        user.setProfilePictureUrl("profile_pictures/40/avatar.webp");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(storage.createPresignedUrlIfValid("profile_pictures/40/avatar.webp"))
                .thenReturn("https://storage.example.test/presigned-avatar.webp");

        ProfilePictureResponse response = service.getProfilePicture(userId);

        assertThat(response.profilePictureUrl()).isEqualTo("https://storage.example.test/presigned-avatar.webp");
        assertThat(response.photoUrl()).isEqualTo("https://storage.example.test/presigned-avatar.webp");
    }

    @Test
    @DisplayName("Should throw 403 when updating or accessing profile picture for suspended user")
    void shouldThrow403ForSuspendedUser() {
        long userId = 50L;
        User user = new User("suspended@joyn.fr", "hash", "CANDIDATE");
        user.setStatus("SUSPENDED");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        MockMultipartFile file = new MockMultipartFile("file", "pic.jpg", "image/jpeg", new byte[]{1});
        assertThatThrownBy(() -> service.updateProfilePicture(userId, file))
                .isInstanceOf(UserProfilePictureException.class)
                .matches(ex -> ((UserProfilePictureException) ex).getStatus() == 403);

        assertThatThrownBy(() -> service.getProfilePicture(userId))
                .isInstanceOf(UserProfilePictureException.class)
                .matches(ex -> ((UserProfilePictureException) ex).getStatus() == 403);
    }
}
