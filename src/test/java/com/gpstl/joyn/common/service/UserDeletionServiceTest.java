
package com.gpstl.joyn.common.service;

import com.gpstl.joyn.candidate.repository.CandidateRepository;
import com.gpstl.joyn.candidate.service.CandidateResumeStorage;
import com.gpstl.joyn.common.model.User;
import com.gpstl.joyn.common.repository.UserRepository;
import com.gpstl.joyn.recruiter.repository.RecruiterRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserDeletionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CandidateRepository candidateRepository;

    @Mock
    private RecruiterRepository recruiterRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private CandidateResumeStorage candidateResumeStorage;

    @InjectMocks
    private UserDeletionService userDeletionService;

    private Jwt createJwt(Long userId, String role) {
        Instant now = Instant.now();

        return new Jwt(
                "test-token",
                now,
                now.plusSeconds(3600),
                Map.of("alg", "HS256"),
                Map.of(
                        "sub", userId.toString(),
                        "role", role
                )
        );
    }

    @Test
    void candidateCannotDeleteAnotherUser() {

        Jwt jwt = createJwt(1L, "CANDIDATE");

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> userDeletionService.deleteUser(
                                2L,
                                jwt,
                                "Test",
                                "127.0.0.1"
                        )
                );

        assertEquals(
                HttpStatus.FORBIDDEN,
                exception.getStatusCode()
        );

        verifyNoInteractions(
                userRepository,
                candidateRepository,
                recruiterRepository,
                jdbcTemplate,
                candidateResumeStorage
        );
    }

    @Test
    void recruiterCannotDeleteAnotherUser() {

        Jwt jwt = createJwt(3L, "RECRUITER");

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> userDeletionService.deleteUser(
                                4L,
                                jwt,
                                null,
                                "127.0.0.1"
                        )
                );

        assertEquals(
                HttpStatus.FORBIDDEN,
                exception.getStatusCode()
        );

        verifyNoInteractions(
                userRepository,
                candidateRepository,
                recruiterRepository,
                jdbcTemplate,
                candidateResumeStorage
        );
    }

    @Test
    void administratorCannotDeleteOwnAccount() {

        Jwt jwt = createJwt(10L, "ADMIN");

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> userDeletionService.deleteUser(
                                10L,
                                jwt,
                                null,
                                "127.0.0.1"
                        )
                );

        assertEquals(
                HttpStatus.FORBIDDEN,
                exception.getStatusCode()
        );

        verifyNoInteractions(
                userRepository,
                candidateRepository,
                recruiterRepository,
                jdbcTemplate,
                candidateResumeStorage
        );
    }

    @Test
    void deletingUnknownUserReturnsNotFound() {

        Jwt jwt = createJwt(1L, "ADMIN");

        when(userRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> userDeletionService.deleteUser(
                                99L,
                                jwt,
                                null,
                                "127.0.0.1"
                        )
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        verify(userRepository, never()).save(any());
    }

    @Test
    void deletingAlreadyDeletedUserReturnsNotFound() {

        Jwt jwt = createJwt(1L, "ADMIN");

        User deletedUser = mock(User.class);

        when(deletedUser.getStatus())
                .thenReturn("DELETED");

        when(userRepository.findById(20L))
                .thenReturn(Optional.of(deletedUser));

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> userDeletionService.deleteUser(
                                20L,
                                jwt,
                                null,
                                "127.0.0.1"
                        )
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        verify(userRepository, never()).save(any());
    }

    @Test
    void candidateCanDeleteOwnAccountAndAuditIsRecorded() {

        Jwt jwt = createJwt(5L, "CANDIDATE");

        User user = mock(User.class);

        when(userRepository.findById(5L))
                .thenReturn(Optional.of(user));

        when(candidateRepository.findByUserId(5L))
                .thenReturn(Optional.empty());

        when(recruiterRepository.findByUserId(5L))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(
                () -> userDeletionService.deleteUser(
                        5L,
                        jwt,
                        "I want to delete my account",
                        "127.0.0.1"
                )
        );

        verify(user).anonymize();

        verify(userRepository).save(user);

        verify(jdbcTemplate).update(
                contains("INSERT INTO user_deletion_audit"),
                eq(5L),
                eq(5L),
                eq("127.0.0.1"),
                eq("I want to delete my account")
        );

        verify(jdbcTemplate).update(
                eq("DELETE FROM auth_tokens WHERE user_id = ?"),
                eq(5L)
        );
    }

    @Test
    void administratorCanDeleteAnotherAccount() {

        Jwt jwt = createJwt(1L, "ADMIN");

        User user = mock(User.class);

        when(userRepository.findById(25L))
                .thenReturn(Optional.of(user));

        when(candidateRepository.findByUserId(25L))
                .thenReturn(Optional.empty());

        when(recruiterRepository.findByUserId(25L))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(
                () -> userDeletionService.deleteUser(
                        25L,
                        jwt,
                        "Administrative deletion",
                        "127.0.0.1"
                )
        );

        verify(user).anonymize();

        verify(userRepository).save(user);

        verify(jdbcTemplate).update(
                contains("INSERT INTO user_deletion_audit"),
                eq(25L),
                eq(1L),
                eq("127.0.0.1"),
                eq("Administrative deletion")
        );
    }
}
