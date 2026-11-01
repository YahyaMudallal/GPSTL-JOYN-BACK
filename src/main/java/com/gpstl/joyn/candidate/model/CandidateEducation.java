package com.gpstl.joyn.candidate.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Formation d'un candidat (table candidate_educations).
 * Aucun trigger updated_at n'existe sur cette table : la date est maintenue côté Java.
 */
@Entity
@Table(name = "candidate_educations")
public class CandidateEducation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private Candidate candidate;

    @Column(name = "school_name", nullable = false)
    private String schoolName;

    @Column(name = "school_address")
    private String schoolAddress;

    @Column(name = "field_of_study", nullable = false)
    private String fieldOfStudy;

    @Enumerated(EnumType.STRING)
    @Column(name = "degree", nullable = false)
    private Degree degree;

    @Enumerated(EnumType.STRING)
    @Column(name = "postbac_year")
    private PostbacYear postbacYear;

    @Column(name = "start_year")
    private Short startYear;

    @Column(name = "end_year")
    private Short endYear;

    @Column(name = "currently_studying", nullable = false)
    private boolean currentlyStudying = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected CandidateEducation() {
    }

    public CandidateEducation(Candidate candidate, String schoolName, String fieldOfStudy, Degree degree) {
        this.candidate = candidate;
        this.schoolName = schoolName;
        this.fieldOfStudy = fieldOfStudy;
        this.degree = degree;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Candidate getCandidate() { return candidate; }

    public String getSchoolName() { return schoolName; }
    public void setSchoolName(String schoolName) { this.schoolName = schoolName; }
    public String getSchoolAddress() { return schoolAddress; }
    public void setSchoolAddress(String schoolAddress) { this.schoolAddress = schoolAddress; }
    public String getFieldOfStudy() { return fieldOfStudy; }
    public void setFieldOfStudy(String fieldOfStudy) { this.fieldOfStudy = fieldOfStudy; }
    public Degree getDegree() { return degree; }
    public void setDegree(Degree degree) { this.degree = degree; }
    public PostbacYear getPostbacYear() { return postbacYear; }
    public void setPostbacYear(PostbacYear postbacYear) { this.postbacYear = postbacYear; }
    public Short getStartYear() { return startYear; }
    public void setStartYear(Short startYear) { this.startYear = startYear; }
    public Short getEndYear() { return endYear; }
    public void setEndYear(Short endYear) { this.endYear = endYear; }
    public boolean isCurrentlyStudying() { return currentlyStudying; }
    public void setCurrentlyStudying(boolean currentlyStudying) { this.currentlyStudying = currentlyStudying; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
