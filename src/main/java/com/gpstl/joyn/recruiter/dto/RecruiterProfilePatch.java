package com.gpstl.joyn.recruiter.dto;

import com.gpstl.joyn.recruiter.exception.RecruiterProfileException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Corps validé de PATCH /api/v1/recruiters/profile, sans aucun accès à la base.
 *
 * Règles :
 *  - seuls firstName et lastName sont modifiables : e-mail, mot de passe, rôle et statut appartiennent
 *    au compte, et l'entreprise détermine les offres auxquelles le recruteur a accès ;
 *  - champ absent : conservé (null dans ce record) ; null explicite : refusé, les colonnes sont NOT NULL ;
 *  - toutes les erreurs sont renvoyées ensemble.
 */
public record RecruiterProfilePatch(String firstName, String lastName) {
    private static final int NAME_MAX = 100;

    public static RecruiterProfilePatch parse(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            throw new RecruiterProfileException(400, "Invalid profile patch",
                    Map.of("body", "Expected a JSON object with firstName or lastName"));
        }
        Map<String, String> errors = new LinkedHashMap<>();
        String firstName = null;
        String lastName = null;
        for (var entry : body.entrySet()) {
            String field = entry.getKey();
            try {
                switch (field) {
                    case "firstName" -> firstName = name(entry.getValue());
                    case "lastName" -> lastName = name(entry.getValue());
                    default -> throw new IllegalArgumentException("This field cannot be modified by this route");
                }
            } catch (IllegalArgumentException ex) {
                errors.put(field, ex.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            throw new RecruiterProfileException(400, "Invalid profile patch", errors);
        }
        return new RecruiterProfilePatch(firstName, lastName);
    }

    private static String name(Object raw) {
        if (!(raw instanceof String text)) throw new IllegalArgumentException("Expected a non-empty string");
        String name = text.trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Must not be blank");
        if (name.length() > NAME_MAX) throw new IllegalArgumentException("Must not exceed " + NAME_MAX + " characters");
        // PostgreSQL refuse le caractère NUL : sans ce contrôle, il donnerait une erreur 500.
        if (name.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Must not contain control characters");
        return name;
    }
}
