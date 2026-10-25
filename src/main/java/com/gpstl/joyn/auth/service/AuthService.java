package com.gpstl.joyn.auth.service;

import com.gpstl.joyn.auth.dto.LoginRequest;
import com.gpstl.joyn.auth.dto.LoginResponse;
import com.gpstl.joyn.auth.exception.AccountSuspendedException;
import com.gpstl.joyn.auth.exception.InvalidCredentialsException;
import com.gpstl.joyn.auth.exception.TooManyAttemptsException;
import com.gpstl.joyn.common.model.User;
import com.gpstl.joyn.common.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * Logique de la connexion (U16) : vérifier les identifiants puis fabriquer le token.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final LoginAttemptService loginAttemptService;

    /**
     * Hash BCrypt "bidon", utilisé quand l'email n'existe pas : on fait quand même une
     * vérification BCrypt pour que la réponse prenne le même temps. Sinon, un attaquant
     * pourrait deviner quels emails existent en mesurant le temps de réponse.
     */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokenService, LoginAttemptService loginAttemptService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.loginAttemptService = loginAttemptService;
        this.dummyHash = passwordEncoder.encode("joyn-dummy-password");
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        // 1. Protection brute-force : trop d'échecs récents -> on refuse sans même vérifier (429).
        long lockedSeconds = loginAttemptService.secondsUntilUnlocked(email);
        if (lockedSeconds > 0) {
            throw new TooManyAttemptsException(lockedSeconds);
        }

        // 2. Vérification des identifiants. Email inconnu et mauvais mot de passe donnent
        //    exactement la même erreur (401), pour ne pas révéler quels emails existent.
        Optional<User> found = userRepository.findByEmailIgnoreCase(email);
        String hashToCheck = found.map(User::getPasswordHash).orElse(dummyHash);
        boolean passwordOk = passwordEncoder.matches(request.password(), hashToCheck);
        if (found.isEmpty() || !passwordOk) {
            loginAttemptService.recordFailure(email);
            throw new InvalidCredentialsException();
        }
        User user = found.get();
        loginAttemptService.recordSuccess(email);

        // 3. Identifiants corrects mais compte suspendu par un admin -> 403.
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new AccountSuspendedException();
        }

        // 4. Tout est bon : on note la date de connexion et on fabrique le token.
        user.setLastLoginAt(Instant.now());
        String token = jwtTokenService.createToken(user);
        return new LoginResponse(token, "Bearer", jwtTokenService.getExpirationSeconds(), user.getRole());
    }
}
