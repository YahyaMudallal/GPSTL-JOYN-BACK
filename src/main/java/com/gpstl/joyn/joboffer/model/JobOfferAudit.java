package com.gpstl.joyn.joboffer.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.OffsetDateTime;

/** Trace l’auteur et la date d’une opération, dans la même transaction que l’offre. */
@Entity
@Table(name = "job_offer_audit")
public class JobOfferAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_offer_id", nullable = false)
    private JobOffer jobOffer;

    @Column(nullable = false)
    private String action;

    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected JobOfferAudit() {}

    public JobOfferAudit(Long userId, JobOffer jobOffer, String action, String reason) {
        this.userId = userId;
        this.jobOffer = jobOffer;
        this.action = action;
        this.reason = reason;
    }
}
