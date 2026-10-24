package com.gpstl.joyn.auth.controller;

import com.gpstl.joyn.auth.dto.LoginRequest;
import com.gpstl.joyn.auth.dto.LoginResponse;
import com.gpstl.joyn.auth.service.AuthService;
import com.gpstl.joyn.joboffer.dto.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Route de connexion (U16). Publique : on n'a évidemment pas encore de token quand on se connecte.
 * Le contrôleur reçoit la requête, la passe au service, et renvoie le token.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentification", description = "Connexion et obtention du token JWT")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Se connecter",
            description = "Vérifie l'email et le mot de passe, puis renvoie un token JWT à envoyer ensuite "
                    + "dans l'en-tête Authorization: Bearer <token>.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    examples = @ExampleObject(value = """
                            {"email": "admin@joyn.dev", "password": "JoynAdmin2026!"}"""))))
    @ApiResponse(responseCode = "200", description = "Connexion réussie",
            content = @Content(schema = @Schema(implementation = LoginResponse.class)))
    @ApiResponse(responseCode = "400", description = "Email ou mot de passe manquant, ou email mal formé",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Email ou mot de passe incorrect",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Compte suspendu",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "429", description = "Trop d'essais ratés, réessayer plus tard (en-tête Retry-After)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
