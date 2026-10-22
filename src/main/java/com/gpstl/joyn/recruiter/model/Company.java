package com.gpstl.joyn.recruiter.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "companies")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;
    private String website;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "siret", columnDefinition = "bpchar(14)", length = 14, unique = true)
    private String siret;

    @Column(name = "email_domain")
    private String emailDomain;

    private String sector;

    @Column(name = "company_size")
    private String companySize;

    private String address;

    @Column(name = "check_siret_found", nullable = false)
    private boolean checkSiretFound = false;

    @Column(name = "verification_status", nullable = false)
    private String verificationStatus = "NOT_VERIFIED";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Company() {}

    public Company(String name) {
        this.name = name;
        this.checkSiretFound = false;
        this.verificationStatus = "NOT_VERIFIED";
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // Getters et Setters
    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSiret() { return siret; }
    public void setSiret(String siret) { this.siret = siret; }
    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getDescription() { return description; }
    public String getWebsite() { return website; }
    public String getLogoUrl() { return logoUrl; }
    public String getSector() { return sector; }
    public String getCompanySize() { return companySize; }
    public String getAddress() { return address; }
}