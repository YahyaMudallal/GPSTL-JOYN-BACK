package com.gpstl.joyn.auth.controller;

import com.gpstl.joyn.auth.exception.AccountSuspendedException;
import com.gpstl.joyn.auth.exception.InvalidCredentialsException;
import com.gpstl.joyn.auth.exception.TooManyAttemptsException;
import com.gpstl.joyn.joboffer.dto.ApiError;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;


/**
 * Transforme les erreurs de la connexion en réponses JSON, avec le meme format ApiError
 * que la création d'offre (R58) : {status, error, message, errors}.
 * AuthExceptionHandler
 */
@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {
    /** 400 : email ou mot de passe manquant, email mal formé. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex){
        List<ApiError.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
        .map(e -> new ApiError.FieldError(e.getField(), e.getDefaultMessage()))
        .toList();
        return error(HttpStatus.BAD_REQUEST, "La requete contient des erreurs", errors);
    }







    /** 400 : corps absent ou JSON mal formé. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex){
        return error(HttpStatus.BAD_REQUEST, "Le corps de la requete n'est pas un JSON valide", List.of());
    }







    /** 401 : email inconnu ou mauvais mot de passe (meme message dans les deux cas).
     */

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex) {
        return error(HttpStatus.UNAUTHORIZED, ex.getMessage(), List.of());
    }



    /** 403: compte suspendu. */
    @ExceptionHandler(AccountSuspendedException.class)
    public ResponseEntity<ApiError> handleSuspended(AccountSuspendedException ex) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage(), List.of());
    }

    /** 429 : trop d'essais. L'en-tete Retry-After dit au front combien de secondes attendre. */
    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<ApiError> handleTooManyAttempts(TooManyAttemptsException ex){
        HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
        return ResponseEntity.status(status)
        .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
        .body(new ApiError(status.value(), status.getReasonPhrase(), ex.getMessage(), List.of()));
    }




    private static ResponseEntity<ApiError> error(HttpStatus status, String message, List<ApiError.FieldError> errors){
        return ResponseEntity.status(status)
        .body(new ApiError(status.value(), status.getReasonPhrase(), message, errors));
    }




}