package com.gpstl.joyn.common.service;

import com.gpstl.joyn.common.model.User;
import com.gpstl.joyn.common.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class UserAccountService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAccountService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Valide les identifiants, hash le mot de passe et sauvegarde l'entité User.
     */
    public User createUserAccount(String rawEmail, String rawPassword, String role) {
        validateCredentials(rawEmail, rawPassword);

        String normalizedEmail = rawEmail.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalArgumentException("Cet email est déjà associé à un compte.");
        }

        String encodedPassword = passwordEncoder.encode(rawPassword);
        return userRepository.saveAndFlush(new User(normalizedEmail, encodedPassword, role));
    }

    private void validateCredentials(String email, String password) {
        if (email == null || email.isBlank() || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new IllegalArgumentException("Format d'email invalide.");
        }
        if (password == null || password.length() < 12) {
            throw new IllegalArgumentException("Le mot de passe doit contenir au moins 12 caractères.");
        }
    }
}