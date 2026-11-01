package com.gpstl.joyn.candidate.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/**
 * Clé primaire composée de candidate_skills (candidate_id, skill_id).
 * JPA exige Serializable, un constructeur sans argument, equals et hashCode
 * pour identifier une ligne par la combinaison des deux colonnes.
 */
@Embeddable
public class CandidateSkillId implements Serializable {

    @Column(name = "candidate_id")
    private Long candidateId;

    @Column(name = "skill_id")
    private Long skillId;

    protected CandidateSkillId() {
    }

    public CandidateSkillId(Long candidateId, Long skillId) {
        this.candidateId = candidateId;
        this.skillId = skillId;
    }

    public Long getCandidateId() { return candidateId; }
    public Long getSkillId() { return skillId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CandidateSkillId that)) return false;
        return Objects.equals(candidateId, that.candidateId) && Objects.equals(skillId, that.skillId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(candidateId, skillId);
    }
}
