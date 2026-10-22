package com.gpstl.joyn.joboffer.model;
import com.gpstl.joyn.common.model.Skill;
import com.gpstl.joyn.recruiter.model.Company;
import com.gpstl.joyn.recruiter.model.RecruiterProfile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Offre d'emploi ou d'alternance (table job_offers).
 */
@Entity
@Table(name = "job_offers")
public class JobOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recruiter_id", nullable = false)
    private RecruiterProfile recruiter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_type_id", nullable = false)
    private ContractType contractType;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "location")
    private String location;

    @Column(name = "salary_min", precision = 12, scale = 2)
    private BigDecimal salaryMin;

    @Column(name = "salary_max", precision = 12, scale = 2)
    private BigDecimal salaryMax;

    /** CHAR(3) en base : le type JDBC CHAR évite une erreur de validation Hibernate (bpchar ≠ varchar). */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "salary_currency", length = 3, nullable = false)
    private String salaryCurrency = "EUR";

    @Enumerated(EnumType.STRING)
    @Column(name = "salary_period", nullable = false)
    private SalaryPeriod salaryPeriod = SalaryPeriod.YEAR;

    @Enumerated(EnumType.STRING)
    @Column(name = "remote_mode", nullable = false)
    private RemoteMode remoteMode = RemoteMode.ONSITE;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OfferStatus status = OfferStatus.DRAFT;

    // --- Champs spécifiques à l'alternance (facultatifs) ---

    @Column(name = "rhythm_type")
    private String rhythmType;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "duration_months")
    private Short durationMonths;

    @Column(name = "degree_level_required")
    private String degreeLevelRequired;

    @Enumerated(EnumType.STRING)
    @Column(name = "experience_level_required")
    private ExperienceLevel experienceLevelRequired;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * Compétences demandées. Seules les colonnes job_offer_id et skill_id sont écrites :
     * required_level, created_at et updated_at prennent leurs valeurs par défaut en base.
     */
    @ManyToMany
    @JoinTable(
            name = "job_offer_skills",
            joinColumns = @JoinColumn(name = "job_offer_id"),
            inverseJoinColumns = @JoinColumn(name = "skill_id"))
    private Set<Skill> skills = new LinkedHashSet<>();

    public JobOffer() {
    }

    public Long getId() { return id; }

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }

    public RecruiterProfile getRecruiter() { return recruiter; }
    public void setRecruiter(RecruiterProfile recruiter) { this.recruiter = recruiter; }

    public ContractType getContractType() { return contractType; }
    public void setContractType(ContractType contractType) { this.contractType = contractType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public BigDecimal getSalaryMin() { return salaryMin; }
    public void setSalaryMin(BigDecimal salaryMin) { this.salaryMin = salaryMin; }

    public BigDecimal getSalaryMax() { return salaryMax; }
    public void setSalaryMax(BigDecimal salaryMax) { this.salaryMax = salaryMax; }

    public String getSalaryCurrency() { return salaryCurrency; }
    public void setSalaryCurrency(String salaryCurrency) { this.salaryCurrency = salaryCurrency; }

    public SalaryPeriod getSalaryPeriod() { return salaryPeriod; }
    public void setSalaryPeriod(SalaryPeriod salaryPeriod) { this.salaryPeriod = salaryPeriod; }

    public RemoteMode getRemoteMode() { return remoteMode; }
    public void setRemoteMode(RemoteMode remoteMode) { this.remoteMode = remoteMode; }

    public OfferStatus getStatus() { return status; }
    public void setStatus(OfferStatus status) { this.status = status; }

    public String getRhythmType() { return rhythmType; }
    public void setRhythmType(String rhythmType) { this.rhythmType = rhythmType; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public Short getDurationMonths() { return durationMonths; }
    public void setDurationMonths(Short durationMonths) { this.durationMonths = durationMonths; }

    public String getDegreeLevelRequired() { return degreeLevelRequired; }
    public void setDegreeLevelRequired(String degreeLevelRequired) { this.degreeLevelRequired = degreeLevelRequired; }

    public ExperienceLevel getExperienceLevelRequired() { return experienceLevelRequired; }
    public void setExperienceLevelRequired(ExperienceLevel experienceLevelRequired) { this.experienceLevelRequired = experienceLevelRequired; }

    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }

    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }

    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    /** Force aussi la mise à jour de updated_at pour un PATCH vide ou limité aux compétences. */
    public void touch() { this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC); }

    public Set<Skill> getSkills() { return skills; }
}
