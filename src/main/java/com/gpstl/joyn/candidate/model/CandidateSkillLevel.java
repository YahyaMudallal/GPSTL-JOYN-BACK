package com.gpstl.joyn.candidate.model;

/**
 * Niveau d'un candidat sur une compétence (colonne candidate_skills.level).
 * Différent de job_offer_skills.required_level, qui n'a pas la valeur LEARNING.
 */
public enum CandidateSkillLevel {
    LEARNING, BASIC, INTERMEDIATE, ADVANCED
}
