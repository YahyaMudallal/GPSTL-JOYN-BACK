package com.gpstl.joyn.candidate.service;

import com.gpstl.joyn.candidate.dto.CandidateProfilePatch;
import com.gpstl.joyn.candidate.dto.CandidateProfileResponse;
import com.gpstl.joyn.candidate.event.CandidateMatchingCriteriaChanged;
import com.gpstl.joyn.candidate.exception.CandidateProfileException;
import com.gpstl.joyn.candidate.model.Candidate;
import com.gpstl.joyn.candidate.model.CandidateEducation;
import com.gpstl.joyn.candidate.model.CandidateExperience;
import com.gpstl.joyn.candidate.model.CandidateSkill;
import com.gpstl.joyn.candidate.repository.CandidateRepository;
import com.gpstl.joyn.common.model.Skill;
import com.gpstl.joyn.common.model.User;
import com.gpstl.joyn.common.repository.SkillRepository;
import com.gpstl.joyn.common.repository.UserRepository;
import com.gpstl.joyn.joboffer.model.ContractType;
import com.gpstl.joyn.joboffer.model.RemoteMode;
import com.gpstl.joyn.joboffer.repository.ContractTypeRepository;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Lecture et mise à jour du profil candidat (CA56, CA68).
 * Candidat : lit et modifie son propre profil, toujours celui du token (sub = users.id).
 * Recruteur et administrateur : lecture seule d'un profil désigné par son id ; seul le candidat
 * peut modifier son profil.
 */
@Service
public class CandidateProfileService {
    private final UserRepository users;
    private final CandidateRepository candidates;
    private final ContractTypeRepository contractTypes;
    private final SkillRepository skills;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher events;

    public CandidateProfileService(UserRepository users, CandidateRepository candidates,
            ContractTypeRepository contractTypes, SkillRepository skills,
            EntityManager entityManager, ApplicationEventPublisher events) {
        this.users = users;
        this.candidates = candidates;
        this.contractTypes = contractTypes;
        this.skills = skills;
        this.entityManager = entityManager;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public CandidateProfileResponse getProfile(long userId) {
        checkCandidateAccount(userId);
        Candidate candidate = candidates.findProfileByUserId(userId)
                .orElseThrow(CandidateProfileService::profileNotFound);
        // Conversion dans la transaction : les sections sont chargées à la demande.
        return CandidateProfileResponse.from(candidate);
    }

    @Transactional
    public CandidateProfileResponse updateProfile(long userId, Map<String, Object> body) {
        // Le compte d'abord : un recruteur reçoit 403 même si son JSON est invalide.
        checkCandidateAccount(userId);
        CandidateProfilePatch patch = CandidateProfilePatch.parse(body);
        return CandidateProfileResponse.from(applyPatch(patch, userId));
    }

    /**
     * Lecture d'un profil désigné par son id (candidate_profiles.id), réservée aux recruteurs et administrateurs.
     *  - administrateur : profil complet, quels que soient les réglages et l'état du compte (modération) ;
     *  - recruteur : profil masqué (is_visible_to_recruiters = false) ou compte suspendu → 404, sans révéler
     *    que le profil existe ; sinon vue filtrée par les réglages de visibilité (voir forRecruiter).
     */
    @Transactional(readOnly = true)
    public CandidateProfileResponse getProfileById(long viewerUserId, long candidateId) {
        User viewer = checkAccount(viewerUserId, Set.of("RECRUITER", "ADMIN"),
                "Only recruiters and administrators can view another candidate's profile");
        Candidate candidate = candidates.findProfileById(candidateId)
                .orElseThrow(CandidateProfileService::profileNotFound);
        if ("ADMIN".equals(viewer.getRole())) {
            return CandidateProfileResponse.from(candidate);
        }
        if (!candidate.isVisibleToRecruiters() || !"ACTIVE".equals(candidate.getUser().getStatus())) {
            throw profileNotFound();
        }
        return CandidateProfileResponse.forRecruiter(candidate);
    }

    /** Étapes de la mise à jour ; le corps a déjà été validé (sans accès à la base) par l'appelant. */
    private Candidate applyPatch(CandidateProfilePatch patch, long userId) {
        // 1. Références résolues AVANT le verrou du profil. findOrCreate peut attendre une autre
        //    transaction qui insère la même compétence : si on détenait déjà le verrou du profil,
        //    deux mises à jour pourraient s'attendre mutuellement (interblocage).
        Set<ContractType> desiredContracts = patch.contractTypes() == null
                ? null : resolveContractTypes(patch.contractTypes());
        List<Skill> desiredSkills = patch.skills() == null ? null : resolveSkills(patch.skills());

        // 2. SELECT ... FOR UPDATE : une autre mise à jour du même profil attend notre commit.
        Candidate candidate = candidates.findByUserIdForUpdate(userId).orElseThrow(CandidateProfileService::profileNotFound);

        // 3. Seuls les champs présents et validés sont appliqués à l'entité gérée par Hibernate.
        patch.values().forEach((field, value) -> apply(candidate, field, value));
        if (desiredContracts != null) syncContractTypes(candidate, desiredContracts);
        if (desiredSkills != null) syncSkills(candidate, desiredSkills);
        if (patch.experiences() != null) replaceExperiences(candidate, patch.experiences());
        if (patch.educations() != null) replaceEducations(candidate, patch.educations());
        candidate.touch();
        candidates.saveAndFlush(candidate);
        // Le trigger PostgreSQL fixe updated_at : recharger sa valeur réelle avant la réponse.
        entityManager.refresh(candidate);

        if (patch.changesMatchingCriteria()) {
            events.publishEvent(new CandidateMatchingCriteriaChanged(candidate.getId()));
        }
        return candidate;
    }

    /** Routes /api/v1/candidates/profile : seul un candidat actif accède à son propre profil. */
    private void checkCandidateAccount(long userId) {
        checkAccount(userId, Set.of("CANDIDATE"), "Only candidates have a candidate profile");
    }

    /**
     * Le rôle et l'état du compte sont relus en base, en plus du contrôle du token par SecurityConfig :
     * un token émis avant une suspension ou un changement de rôle ne suffit pas.
     * Utilisateur absent : 401 ; compte suspendu ou rôle non autorisé : 403.
     */
    private User checkAccount(long userId, Set<String> allowedRoles, String forbiddenMessage) {
        User user = users.findById(userId)
                .orElseThrow(() -> new CandidateProfileException(401, "Authenticated user does not exist"));
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new CandidateProfileException(403, "Account is suspended");
        }
        if (!allowedRoles.contains(user.getRole())) {
            throw new CandidateProfileException(403, forbiddenMessage);
        }
        return user;
    }

    private static CandidateProfileException profileNotFound() {
        return new CandidateProfileException(404, "Candidate profile not found");
    }

    /** Une seule requête sur la table de référence ; tous les codes inconnus sont signalés ensemble. */
    private Set<ContractType> resolveContractTypes(List<String> codes) {
        Map<String, ContractType> byCode = contractTypes.findAll().stream()
                .collect(Collectors.toMap(type -> type.getCode().toUpperCase(Locale.ROOT), type -> type,
                        (first, second) -> first, TreeMap::new));
        Set<ContractType> result = new LinkedHashSet<>();
        List<String> unknown = new ArrayList<>();
        for (String code : codes) {
            ContractType type = byCode.get(code);
            if (type == null) unknown.add(code); else result.add(type);
        }
        if (!unknown.isEmpty()) {
            throw new CandidateProfileException(400, "Invalid profile patch", Map.of(
                    "preferences.contract_types",
                    "Unknown contract types " + unknown + "; allowed values: " + byCode.keySet()));
        }
        return result;
    }

    /** Ordre alphabétique stable : deux transactions insèrent les mêmes compétences dans le même ordre. */
    private List<Skill> resolveSkills(List<String> names) {
        return names.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .map(skills::findOrCreate)
                .toList();
    }

    private static void apply(Candidate c, String field, Object value) {
        switch (field) {
            case "first_name" -> c.setFirstName((String) value);
            case "last_name" -> c.setLastName((String) value);
            case "headline" -> c.setHeadline((String) value);
            case "bio" -> c.setBio((String) value);
            case "location" -> c.setLocation((String) value);
            case "address" -> c.setAddress((String) value);
            case "phone" -> c.setPhone((String) value);
            case "photo_url" -> c.setPhotoUrl((String) value);
            case "years_of_experience" -> c.setYearsOfExperience((Short) value);
            case "links.github" -> c.setGithubUrl((String) value);
            case "links.linkedin" -> c.setLinkedinUrl((String) value);
            case "links.portfolio" -> c.setPortfolioUrl((String) value);
            case "alternance.rhythm_type" -> c.setRhythmType((String) value);
            case "alternance.available_from" -> c.setAvailableFrom((LocalDate) value);
            case "alternance.contract_duration_months" -> c.setContractDurationMonths((Short) value);
            case "preferences.remote_policy" -> c.setPrefRemotePolicy((RemoteMode) value);
            case "preferences.min_salary" -> c.setPrefMinSalary((BigDecimal) value);
            case "preferences.currency" -> c.setPrefSalaryCurrency((String) value);
            case "visibility.visible_to_recruiters" -> c.setVisibleToRecruiters((Boolean) value);
            case "visibility.visible_to_others" -> c.setVisibleToOthers((Boolean) value);
            case "visibility.show_last_name" -> c.setShowLastName((Boolean) value);
            case "visibility.show_contact_links" -> c.setShowContactLinks((Boolean) value);
            default -> throw new IllegalStateException("Unvalidated patch field: " + field);
        }
    }

    /** Modifier le Set géré : Hibernate n'insère ou ne supprime que les lignes de jointure changées. */
    private static void syncContractTypes(Candidate candidate, Set<ContractType> desired) {
        candidate.getPreferredContractTypes().retainAll(desired);
        candidate.getPreferredContractTypes().addAll(desired);
    }

    /** Les compétences conservées gardent leur ligne, donc leur niveau ; les nouvelles prennent INTERMEDIATE. */
    private static void syncSkills(Candidate candidate, List<Skill> desired) {
        Set<Long> desiredIds = desired.stream().map(Skill::getId).collect(Collectors.toSet());
        List<CandidateSkill> current = candidate.getSkills();
        current.removeIf(link -> !desiredIds.contains(link.getSkill().getId()));
        Set<Long> kept = new HashSet<>();
        current.forEach(link -> kept.add(link.getSkill().getId()));
        for (Skill skill : desired) {
            if (!kept.contains(skill.getId())) current.add(new CandidateSkill(candidate, skill));
        }
    }

    /** Remplacement complet : orphanRemoval supprime les anciennes lignes, l'ordre envoyé est conservé. */
    private static void replaceExperiences(Candidate candidate, List<CandidateProfilePatch.ExperienceInput> inputs) {
        candidate.getExperiences().clear();
        for (var input : inputs) {
            CandidateExperience experience = new CandidateExperience(candidate, input.title());
            experience.setOrganization(input.organization());
            experience.setDescription(input.description());
            experience.setStartDate(input.startDate());
            experience.setEndDate(input.endDate());
            experience.setCurrentlyWorking(input.currentlyWorking());
            candidate.getExperiences().add(experience);
        }
    }

    private static void replaceEducations(Candidate candidate, List<CandidateProfilePatch.EducationInput> inputs) {
        candidate.getEducations().clear();
        for (var input : inputs) {
            CandidateEducation education = new CandidateEducation(
                    candidate, input.schoolName(), input.fieldOfStudy(), input.degree());
            education.setSchoolAddress(input.schoolAddress());
            education.setPostbacYear(input.postbacYear());
            education.setStartYear(input.startYear());
            education.setEndYear(input.endYear());
            education.setCurrentlyStudying(input.currentlyStudying());
            candidate.getEducations().add(education);
        }
    }
}
