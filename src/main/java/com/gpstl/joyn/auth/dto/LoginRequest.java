package com.gpstl.joyn.auth.dto;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;





/**
 * Corps de la requete POST /api/v1/auth/login.
 * Exemple: {"email": "admin@joyn.dev" , "password":"JoynAdmin2026!"}
 * LoginRequest
 */
public record LoginRequest (

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "L'email n'est pas valide")
    String email,

    @NotBlank(message = "Le mot de passe est obligatoire")
    String password){}
    

