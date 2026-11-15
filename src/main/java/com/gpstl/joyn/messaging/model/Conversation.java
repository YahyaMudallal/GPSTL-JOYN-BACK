package com.gpstl.joyn.messaging.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/**
 * Une conversation (table conversations, créée en V1).
 *
 * Elle vient soit d'une candidature (applicationId), soit d'un match (matchId) :
 * la base impose qu'au moins l'un des deux soit renseigné, et chacun est UNIQUE
 * (une seule conversation par candidature, une seule par match).
 *
 * Les deux liens sont gardés en simples ids : les matchs n'ont pas encore d'entité Java,
 * et la messagerie n'a pas besoin de charger la candidature entière.
 */
@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", unique = true)
    private Long applicationId;

    @Column(name = "match_id", unique = true)
    private Long matchId;

    @Column(name = "is_unlocked", nullable = false)
    private boolean unlocked;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Conversation() {
    }

    public Long getId() {
        return id;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public Long getMatchId() {
        return matchId;
    }

    public boolean isUnlocked() {
        return unlocked;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    /** Marque la conversation comme active maintenant (nouveau message) : elle remonte en tête de liste. */
    public void touch() {
        this.updatedAt = OffsetDateTime.now();
    }
}
