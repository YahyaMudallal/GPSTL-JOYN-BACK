package com.gpstl.joyn.candidate.model;

import com.gpstl.joyn.common.model.Skill;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Compétence d'un candidat (table de jointure candidate_skills).
 * Pas de @ManyToMany ici : la jointure porte une donnée propre (level),
 * elle devient donc une entité reliée à Candidate et à Skill par deux @ManyToOne.
 */
@Entity
@Table(name = "candidate_skills")
public class CandidateSkill {

    @EmbeddedId
    private CandidateSkillId id;

    /** @MapsId : candidate_id de la clé primaire est rempli à partir de cette relation. */
    @MapsId("candidateId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id")
    private Candidate candidate;

    @MapsId("skillId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id")
    private Skill skill;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false)
    private CandidateSkillLevel level = CandidateSkillLevel.INTERMEDIATE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected CandidateSkill() {
    }

    /** Le candidat et la compétence doivent déjà être enregistrés (leurs id forment la clé). */
    public CandidateSkill(Candidate candidate, Skill skill) {
        this.id = new CandidateSkillId(candidate.getId(), skill.getId());
        this.candidate = candidate;
        this.skill = skill;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }

    public CandidateSkillId getId() { return id; }
    public Candidate getCandidate() { return candidate; }
    public Skill getSkill() { return skill; }

    public CandidateSkillLevel getLevel() { return level; }
    public void setLevel(CandidateSkillLevel level) { this.level = level; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
