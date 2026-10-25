package com.gpstl.joyn.application.model;

import com.gpstl.joyn.candidate.model.Candidate;
import com.gpstl.joyn.joboffer.model.JobOffer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "applications", uniqueConstraints = @UniqueConstraint(
        name = "uq_application_candidate_offer",
        columnNames = {"candidate_id", "job_offer_id"}))
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private Candidate candidate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_offer_id", nullable = false)
    private JobOffer jobOffer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "status_id", nullable = false)
    private ApplicationStatus status;

    @Column(name = "viewed_at")
    private OffsetDateTime viewedAt;

    @Column(name = "cover_message", columnDefinition = "TEXT")
    private String coverMessage;

    @Column(name = "resume_url", columnDefinition = "TEXT")
    private String resumeUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Application() {
    }

    public Application(
            Candidate candidate, 
            JobOffer jobOffer,
            ApplicationStatus status,
            String coverMessage, 
            String resumeUrl) {
        this.candidate = candidate;
        this.jobOffer = jobOffer;
        this.status = status;
        this.coverMessage = coverMessage;
        this.resumeUrl = resumeUrl;
    }

    public Long getId() {
        return id;
    }

    public Candidate getCandidate() {
        return candidate;
    }

    public JobOffer getJobOffer() {
        return jobOffer;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(ApplicationStatus status) {
        this.status = status;
    }

    public void setViewedAt(OffsetDateTime viewedAt) {
        this.viewedAt = viewedAt;
    }

    public OffsetDateTime getViewedAt() {
        return viewedAt;
    }

    public String getCoverMessage() {
        return coverMessage;
    }

    public String getResumeUrl() {
        return resumeUrl;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
