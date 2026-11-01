package com.gpstl.joyn.candidate.model;

import com.gpstl.joyn.common.model.User;
import com.gpstl.joyn.joboffer.model.ContractType;
import com.gpstl.joyn.joboffer.model.RemoteMode;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Profil candidat (table candidate_profiles).
 * Les champs NOT NULL avec une valeur par défaut en base sont initialisés ici aussi :
 * Hibernate écrit toutes les colonnes à l'INSERT, un null ferait échouer l'inscription.
 */
@Entity
@Table(name = "candidate_profiles")
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "headline")
    private String headline;

    @Column(name = "bio")
    private String bio;

    @Column(name = "location")
    private String location;

    /** La colonne s'appelle « adress » en base ; le nom Java et JSON reste « address ». */
    @Column(name = "adress")
    private String address;

    @Column(name = "phone")
    private String phone;

    @Column(name = "years_of_experience")
    private Short yearsOfExperience;

    // --- Champs spécifiques à l'alternance (facultatifs) ---

    @Column(name = "rhythm_type")
    private String rhythmType;

    @Column(name = "available_from")
    private LocalDate availableFrom;

    @Column(name = "contract_duration_months")
    private Short contractDurationMonths;

    // --- Liens externes ---

    @Column(name = "github_url")
    private String githubUrl;

    @Column(name = "linkedin_url")
    private String linkedinUrl;

    @Column(name = "portfolio_url")
    private String portfolioUrl;

    // --- Visibilité du profil ---

    @Column(name = "is_visible_to_recruiters", nullable = false)
    private boolean visibleToRecruiters = true;

    @Column(name = "is_visible_to_others", nullable = false)
    private boolean visibleToOthers = true;

    @Column(name = "show_last_name", nullable = false)
    private boolean showLastName = true;

    @Column(name = "show_contact_links", nullable = false)
    private boolean showContactLinks = true;

    // --- Médias ---

    @Column(name = "resume_url")
    private String resumeUrl;

    @Column(name = "photo_url")
    private String photoUrl;

    // --- Préférences de recherche ---

    /** Mêmes valeurs que job_offers.remote_mode, pour comparer directement lors du matching. */
    @Enumerated(EnumType.STRING)
    @Column(name = "pref_remote_policy")
    private RemoteMode prefRemotePolicy;

    @Column(name = "pref_min_salary", precision = 12, scale = 2)
    private BigDecimal prefMinSalary;

    /** CHAR(3) en base : le type JDBC CHAR évite une erreur de validation Hibernate (bpchar ≠ varchar). */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "pref_salary_currency", length = 3, nullable = false)
    private String prefSalaryCurrency = "EUR";

    /**
     * Types de contrat recherchés. Pas de setter : le service modifie ce Set géré par Hibernate,
     * qui n'insère ou ne supprime alors que les lignes de jointure réellement changées.
     */
    @ManyToMany
    @JoinTable(
            name = "candidate_preferred_contract_types",
            joinColumns = @JoinColumn(name = "candidate_id"),
            inverseJoinColumns = @JoinColumn(name = "contract_type_id"))
    private Set<ContractType> preferredContractTypes = new LinkedHashSet<>();

    // --- Sections liées (une ligne par élément dans leur propre table) ---
    // cascade ALL : enregistrées avec le profil ; orphanRemoval : un élément retiré
    // de la liste est supprimé en base, ce qui permet de remplacer une section entière.
    // Pas de setter : le service modifie ces listes gérées par Hibernate.

    /** Tri par id : après un remplacement, l'ordre envoyé par le client est conservé. */
    @OneToMany(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<CandidateExperience> experiences = new ArrayList<>();

    @OneToMany(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<CandidateEducation> educations = new ArrayList<>();

    @OneToMany(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CandidateSkill> skills = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Candidate() {
    }

    public Candidate(User user, String firstName, String lastName, String resumeUrl) {
        this.user = user;
        this.firstName = firstName;
        this.lastName = lastName;
        this.resumeUrl = resumeUrl;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Marque la ligne comme modifiée même si seules les sections liées ont changé :
     * Hibernate émet alors un UPDATE et le trigger PostgreSQL met à jour updated_at.
     */
    public void touch() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public User getUser() { return user; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Short getYearsOfExperience() { return yearsOfExperience; }
    public void setYearsOfExperience(Short yearsOfExperience) { this.yearsOfExperience = yearsOfExperience; }

    public String getRhythmType() { return rhythmType; }
    public void setRhythmType(String rhythmType) { this.rhythmType = rhythmType; }
    public LocalDate getAvailableFrom() { return availableFrom; }
    public void setAvailableFrom(LocalDate availableFrom) { this.availableFrom = availableFrom; }
    public Short getContractDurationMonths() { return contractDurationMonths; }
    public void setContractDurationMonths(Short contractDurationMonths) { this.contractDurationMonths = contractDurationMonths; }

    public String getGithubUrl() { return githubUrl; }
    public void setGithubUrl(String githubUrl) { this.githubUrl = githubUrl; }
    public String getLinkedinUrl() { return linkedinUrl; }
    public void setLinkedinUrl(String linkedinUrl) { this.linkedinUrl = linkedinUrl; }
    public String getPortfolioUrl() { return portfolioUrl; }
    public void setPortfolioUrl(String portfolioUrl) { this.portfolioUrl = portfolioUrl; }

    public boolean isVisibleToRecruiters() { return visibleToRecruiters; }
    public void setVisibleToRecruiters(boolean visibleToRecruiters) { this.visibleToRecruiters = visibleToRecruiters; }
    public boolean isVisibleToOthers() { return visibleToOthers; }
    public void setVisibleToOthers(boolean visibleToOthers) { this.visibleToOthers = visibleToOthers; }
    public boolean isShowLastName() { return showLastName; }
    public void setShowLastName(boolean showLastName) { this.showLastName = showLastName; }
    public boolean isShowContactLinks() { return showContactLinks; }
    public void setShowContactLinks(boolean showContactLinks) { this.showContactLinks = showContactLinks; }

    public String getResumeUrl() { return resumeUrl; }
    public void setResumeUrl(String resumeUrl) { this.resumeUrl = resumeUrl; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public RemoteMode getPrefRemotePolicy() { return prefRemotePolicy; }
    public void setPrefRemotePolicy(RemoteMode prefRemotePolicy) { this.prefRemotePolicy = prefRemotePolicy; }
    public BigDecimal getPrefMinSalary() { return prefMinSalary; }
    public void setPrefMinSalary(BigDecimal prefMinSalary) { this.prefMinSalary = prefMinSalary; }
    public String getPrefSalaryCurrency() { return prefSalaryCurrency; }
    public void setPrefSalaryCurrency(String prefSalaryCurrency) { this.prefSalaryCurrency = prefSalaryCurrency; }
    public Set<ContractType> getPreferredContractTypes() { return preferredContractTypes; }

    public List<CandidateExperience> getExperiences() { return experiences; }
    public List<CandidateEducation> getEducations() { return educations; }
    public List<CandidateSkill> getSkills() { return skills; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
