package com.gpstl.joyn.recruiter.models;

import jakarta.persistence.*;
import java.time.Instant;

import com.gpstl.joyn.common.models.User;

@Entity
@Table(name = "recruiter_profiles")
public class RecruiterProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public RecruiterProfile() {}

    public RecruiterProfile(User user, Company company, String firstName, String lastName) {
        this.user = user;
        this.company = company;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Company getCompany() { return company; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
}