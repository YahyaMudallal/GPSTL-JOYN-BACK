package com.gpstl.joyn.joboffer.service;

import com.gpstl.joyn.joboffer.dto.CreateJobOfferRequest;
import com.gpstl.joyn.joboffer.dto.JobOfferResponse;
import com.gpstl.joyn.entity.ContractType;
import com.gpstl.joyn.entity.ExperienceLevel;
import com.gpstl.joyn.entity.JobOffer;
import com.gpstl.joyn.entity.OfferStatus;
import com.gpstl.joyn.recruiter.models.RecruiterProfile;
import com.gpstl.joyn.recruiter.repositories.RecruiterProfileRepository;
import com.gpstl.joyn.entity.RemoteMode;
import com.gpstl.joyn.entity.SalaryPeriod;
import com.gpstl.joyn.entity.Skill;
import com.gpstl.joyn.joboffer.exception.InvalidFieldException;
import com.gpstl.joyn.joboffer.exception.RecruiterProfileNotFoundException;
import com.gpstl.joyn.repository.ContractTypeRepository;
import com.gpstl.joyn.repository.JobOfferRepository;

import com.gpstl.joyn.repository.SkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Création d'offres par un recruteur (tâche R58).
 */
@Service
public class JobOfferService {

    private final RecruiterProfileRepository recruiterProfileRepository;
    private final ContractTypeRepository contractTypeRepository;
    private final SkillRepository skillRepository;
    private final JobOfferRepository jobOfferRepository;

    public JobOfferService(RecruiterProfileRepository recruiterProfileRepository,
                           ContractTypeRepository contractTypeRepository,
                           SkillRepository skillRepository,
                           JobOfferRepository jobOfferRepository) {
        this.recruiterProfileRepository = recruiterProfileRepository;
        this.contractTypeRepository = contractTypeRepository;
        this.skillRepository = skillRepository;
        this.jobOfferRepository = jobOfferRepository;
    }

    /**
     * Crée une offre pour l'entreprise du recruteur connecté.
     * Tout se fait dans une seule transaction : si l'enregistrement d'une compétence échoue,
     * l'offre n'est pas créée non plus (rollback).
     *
     * @param userId  identifiant du compte connecté (claim "sub" du token JWT)
     * @param request données envoyées par le client, déjà validées par le contrôleur
     */
    @Transactional
    public JobOfferResponse createJobOffer(Long userId, CreateJobOfferRequest request) {
        // 1. Le recruteur et son entreprise viennent du token, jamais du corps de la requête :
        //    impossible de créer une offre pour le compte d'une autre entreprise.
        RecruiterProfile recruiter = recruiterProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new RecruiterProfileNotFoundException(String.valueOf(userId)));

        // 2. Le type de contrat doit exister dans la table de référence contract_types.
        ContractType contractType = contractTypeRepository.findByCodeIgnoreCase(request.contractType().trim())
                .orElseThrow(() -> new InvalidFieldException("contract_type",
                        "Type de contrat inconnu. Valeurs acceptées : " + availableContractCodes()));

        JobOffer offer = new JobOffer();
        offer.setCompany(recruiter.getCompany());
        offer.setRecruiter(recruiter);
        offer.setContractType(contractType);
        offer.setTitle(request.title().trim());
        offer.setDescription(request.description().trim());
        offer.setLocation(request.location().trim());
        offer.setRemoteMode(RemoteMode.valueOf(request.remotePolicy()));
        offer.setExperienceLevelRequired(toExperienceLevel(request.experienceLevel()));
        offer.setSalaryMin(request.salaryMin());
        offer.setSalaryMax(request.salaryMax());
        if (request.currency() != null) {
            offer.setSalaryCurrency(request.currency());
        }
        if (request.salaryPeriod() != null) {
            offer.setSalaryPeriod(SalaryPeriod.valueOf(request.salaryPeriod()));
        }

        // Champs propres à l'alternance (facultatifs)
        offer.setRhythmType(blankToNull(request.rhythmType()));
        offer.setStartDate(request.startDate());
        offer.setDurationMonths(request.durationMonths() == null ? null : request.durationMonths().shortValue());
        offer.setDegreeLevelRequired(request.degreeLevelRequired());

        // 3. Statut : publiée par défaut (visible des candidats), ou brouillon si demandé.
        OfferStatus status = "DRAFT".equals(request.status()) ? OfferStatus.DRAFT : OfferStatus.PUBLISHED;
        offer.setStatus(status);
        if (status == OfferStatus.PUBLISHED) {
            offer.setPublishedAt(OffsetDateTime.now(ZoneOffset.UTC));
        }

        // 4. Compétences : on réutilise celles du catalogue, on crée celles qui n'existent pas.
        for (String name : normalizeSkills(request.skills())) {
            Skill skill = skillRepository.findByNameIgnoreCase(name)
                    .orElseGet(() -> skillRepository.save(new Skill(name)));
            offer.getSkills().add(skill);
        }

        // Pas de compteurs de métriques à initialiser : vues, likes et candidatures
        // se calculent à partir des tables job_swipes et applications.
        JobOffer saved = jobOfferRepository.saveAndFlush(offer);
        return JobOfferResponse.from(saved);
    }

    /** Accepte aussi les variantes de la spécification (MID_LEVEL -> MID). */
    private static ExperienceLevel toExperienceLevel(String value) {
        if (value == null) {
            return null;
        }
        return ExperienceLevel.valueOf(value.replace("_LEVEL", ""));
    }

    /** Supprime les espaces et les doublons ("React", " react " -> une seule compétence). */
    private static List<String> normalizeSkills(List<String> skills) {
        Map<String, String> unique = new LinkedHashMap<>();
        for (String skill : skills) {
            String trimmed = skill.trim();
            unique.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
        }
        return List.copyOf(unique.values());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String availableContractCodes() {
        return contractTypeRepository.findAll().stream()
                .map(ContractType::getCode)
                .sorted()
                .collect(Collectors.joining(", "));
    }
}
