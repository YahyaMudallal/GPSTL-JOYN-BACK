package com.gpstl.joyn.common.service;

import com.gpstl.joyn.common.model.User;
import com.gpstl.joyn.common.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserAccountService userAccountService;

    @Test
    @DisplayName("createUserAccount() - Succès avec email nettoyé et mot de passe encodé")
    void createUserAccount_Success() {
        String email = "  Test.User@Domain.COM ";
        String rawPassword = "password-with-12-chars";
        String encodedHash = "bcrypt-hashed-string";

        when(userRepository.existsByEmailIgnoreCase("test.user@domain.com")).thenReturn(false);
        when(passwordEncoder.encode(rawPassword)).thenReturn(encodedHash);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userAccountService.createUserAccount(email, rawPassword, "RECRUITER");

        assertThat(result.getEmail()).isEqualTo("test.user@domain.com");
        assertThat(result.getPasswordHash()).isEqualTo(encodedHash);
        assertThat(result.getRole()).isEqualTo("RECRUITER");
        verify(passwordEncoder).encode(rawPassword);
    }

    @Test
    @DisplayName("createUserAccount() - Rejette un mot de passe de moins de 12 caractères")
    void createUserAccount_PasswordTooShort_ThrowsException() {
        assertThatThrownBy(() -> userAccountService.createUserAccount("valid@test.com", "short-pwd", "CANDIDATE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Le mot de passe doit contenir au moins 12 caractères.");

        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("createUserAccount() - Rejette un format d'email invalide")
    void createUserAccount_InvalidEmail_ThrowsException() {
        assertThatThrownBy(() -> userAccountService.createUserAccount("not-an-email", "valid-password-123", "CANDIDATE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Format d'email invalide.");
    }

    @Test
    @DisplayName("createUserAccount() - Rejette un email déjà existant")
    void createUserAccount_DuplicateEmail_ThrowsException() {
        when(userRepository.existsByEmailIgnoreCase("duplicate@test.com")).thenReturn(true);

        assertThatThrownBy(() -> userAccountService.createUserAccount("duplicate@test.com", "valid-password-123", "CANDIDATE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cet email est déjà associé à un compte.");
    }
}