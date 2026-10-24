package com.gpstl.joyn.candidate.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;

/** Trace d'une modification de profil faite par un administrateur, dans la même transaction que la modification. */
@Entity
@Table(name = "candidate_profile_audit")
public class CandidateProfileAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private Candidate candidate;

    @Column(name = "action", nullable = false)
    private String action;

    /** Chemins JSON modifiés, séparés par des virgules (ex. « bio,preferences.min_salary »). */
    @Column(name = "changed_fields", nullable = false)
    private String changedFields;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected CandidateProfileAudit() {
    }

    public CandidateProfileAudit(Long actorUserId, Candidate candidate, String action, List<String> changedFields) {
        this.actorUserId = actorUserId;
        this.candidate = candidate;
        this.action = action;
        this.changedFields = String.join(",", changedFields);
    }

    public Long getId() { return id; }
    public Long getActorUserId() { return actorUserId; }
    public Candidate getCandidate() { return candidate; }
    public String getAction() { return action; }
    public String getChangedFields() { return changedFields; }
    public Instant getCreatedAt() { return createdAt; }
}
