package com.gpstl.joyn.candidate.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gpstl.joyn.candidate.model.Candidate;
import com.gpstl.joyn.candidate.model.CandidateEducation;
import com.gpstl.joyn.candidate.model.CandidateExperience;
import com.gpstl.joyn.joboffer.model.ContractType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Profil complet du candidat, renvoyé par GET et PATCH /api/v1/candidates/profile.
 * On n'expose jamais l'entité JPA : elle contient le compte (users) et des collections
 * chargées à la demande, qui échoueraient une fois la transaction terminée.
 */
public record CandidateProfileResponse(
        Long id,
        String email,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        String headline,
        String bio,
        String phone,
        String location,
        String address,
        @JsonProperty("years_of_experience") Integer yearsOfExperience,
        @JsonProperty("photo_url") String photoUrl,
        @JsonProperty("cv_url") String cvUrl,
        Links links,
        Alternance alternance,
        Preferences preferences,
        Visibility visibility,
        List<String> skills,
        List<Experience> experiences,
        List<Education> educations,
        @JsonProperty("updated_at") Instant updatedAt
) {

    public record Links(String github, String linkedin, String portfolio) {}

    public record Alternance(
            @JsonProperty("rhythm_type") String rhythmType,
            @JsonProperty("available_from") LocalDate availableFrom,
            @JsonProperty("contract_duration_months") Integer contractDurationMonths
    ) {}

    public record Preferences(
            @JsonProperty("contract_types") List<String> contractTypes,
            @JsonProperty("remote_policy") String remotePolicy,
            @JsonProperty("min_salary") Long minSalary,
            String currency
    ) {}

    public record Visibility(
            @JsonProperty("visible_to_recruiters") boolean visibleToRecruiters,
            @JsonProperty("visible_to_others") boolean visibleToOthers,
            @JsonProperty("show_last_name") boolean showLastName,
            @JsonProperty("show_contact_links") boolean showContactLinks
    ) {}

    public record Experience(
            Long id,
            String title,
            String organization,
            String description,
            @JsonProperty("start_date") LocalDate startDate,
            @JsonProperty("end_date") LocalDate endDate,
            @JsonProperty("currently_working") boolean currentlyWorking
    ) {}

    public record Education(
            Long id,
            @JsonProperty("school_name") String schoolName,
            @JsonProperty("school_address") String schoolAddress,
            @JsonProperty("field_of_study") String fieldOfStudy,
            String degree,
            @JsonProperty("postbac_year") String postbacYear,
            @JsonProperty("start_year") Integer startYear,
            @JsonProperty("end_year") Integer endYear,
            @JsonProperty("currently_studying") boolean currentlyStudying
    ) {}

    /**
     * À appeler dans la transaction du service : l'email et les sections liées
     * sont chargés à la demande depuis la base.
     */
    public static CandidateProfileResponse from(Candidate c) {
        return new CandidateProfileResponse(
                c.getId(),
                c.getUser().getEmail(),
                c.getFirstName(),
                c.getLastName(),
                c.getHeadline(),
                c.getBio(),
                c.getPhone(),
                c.getLocation(),
                c.getAddress(),
                toInteger(c.getYearsOfExperience()),
                c.getPhotoUrl(),
                c.getResumeUrl(),
                new Links(c.getGithubUrl(), c.getLinkedinUrl(), c.getPortfolioUrl()),
                new Alternance(c.getRhythmType(), c.getAvailableFrom(), toInteger(c.getContractDurationMonths())),
                new Preferences(
                        // Tri par code : l'ordre de la réponse ne dépend pas de l'ordre de chargement.
                        c.getPreferredContractTypes().stream().map(ContractType::getCode).sorted().toList(),
                        c.getPrefRemotePolicy() == null ? null : c.getPrefRemotePolicy().name(),
                        // Le salaire est validé comme entier : 48000 plutôt que 48000.00 dans le JSON.
                        c.getPrefMinSalary() == null ? null : c.getPrefMinSalary().longValue(),
                        c.getPrefSalaryCurrency()),
                new Visibility(c.isVisibleToRecruiters(), c.isVisibleToOthers(),
                        c.isShowLastName(), c.isShowContactLinks()),
                c.getSkills().stream().map(skill -> skill.getSkill().getName()).sorted(String.CASE_INSENSITIVE_ORDER).toList(),
                c.getExperiences().stream().map(CandidateProfileResponse::toExperience).toList(),
                c.getEducations().stream().map(CandidateProfileResponse::toEducation).toList(),
                c.getUpdatedAt());
    }

    /**
     * Vue d'un recruteur : les données du candidat, filtrées par ses réglages de visibilité
     * (« story sécurité »). Le cas is_visible_to_recruiters = false (profil masqué) est traité
     * par le service, qui répond 404. Ici :
     *  - show_last_name = false : nom réduit à son initiale (« Mudallal » devient « M. ») ;
     *  - show_contact_links = false : email, téléphone, adresse et liens externes masqués,
     *    pour que le premier contact passe par la plateforme ;
     *  - les réglages eux-mêmes (visibility) ne sont pas exposés au recruteur.
     * L'administrateur, lui, reçoit la vue complète (from).
     */
    public static CandidateProfileResponse forRecruiter(Candidate c) {
        CandidateProfileResponse full = from(c);
        boolean contact = c.isShowContactLinks();
        return new CandidateProfileResponse(
                full.id(),
                contact ? full.email() : null,
                full.firstName(),
                c.isShowLastName() ? full.lastName() : initial(full.lastName()),
                full.headline(),
                full.bio(),
                contact ? full.phone() : null,
                full.location(),
                contact ? full.address() : null,
                full.yearsOfExperience(),
                full.photoUrl(),
                full.cvUrl(),
                contact ? full.links() : new Links(null, null, null),
                full.alternance(),
                full.preferences(),
                null,
                full.skills(),
                full.experiences(),
                full.educations(),
                full.updatedAt());
    }

    private static String initial(String lastName) {
        return lastName == null || lastName.isBlank() ? null : lastName.substring(0, 1).toUpperCase() + ".";
    }

    private static Experience toExperience(CandidateExperience e) {
        return new Experience(e.getId(), e.getTitle(), e.getOrganization(), e.getDescription(),
                e.getStartDate(), e.getEndDate(), e.isCurrentlyWorking());
    }

    private static Education toEducation(CandidateEducation e) {
        return new Education(e.getId(), e.getSchoolName(), e.getSchoolAddress(), e.getFieldOfStudy(),
                e.getDegree().name(),
                e.getPostbacYear() == null ? null : e.getPostbacYear().name(),
                toInteger(e.getStartYear()), toInteger(e.getEndYear()), e.isCurrentlyStudying());
    }

    private static Integer toInteger(Short value) {
        return value == null ? null : value.intValue();
    }
}
