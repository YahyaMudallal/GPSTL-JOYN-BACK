package com.gpstl.joyn.joboffer.exception;

/**
 * Le token est valide et porte le rôle recruteur, mais aucun profil recruteur
 * n'est rattaché à ce compte : on ne sait pas pour quelle entreprise créer l'offre.
 * Renvoyée au client en 403.
 */
public class RecruiterProfileNotFoundException extends RuntimeException {

    public RecruiterProfileNotFoundException(String subject) {
        super("Aucun profil recruteur n'est associé à l'utilisateur " + subject);
    }
}
