package com.gpstl.joyn.candidate.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Transporte le statut HTTP et les erreurs par champ des routes du profil candidat jusqu'au gestionnaire d'exceptions. */
public class CandidateProfileException extends RuntimeException {
    private final int status;
    private final Map<String, String> errors;

    public CandidateProfileException(int status, String message) {
        this(status, message, Map.of());
    }

    public CandidateProfileException(int status, String message, Map<String, String> errors) {
        super(message);
        this.status = status;
        // Copie qui garde l'ordre des champs du corps reçu (Map.copyOf le mélangerait).
        this.errors = Collections.unmodifiableMap(new LinkedHashMap<>(errors));
    }

    public int getStatus() { return status; }
    public Map<String, String> getErrors() { return errors; }
}
