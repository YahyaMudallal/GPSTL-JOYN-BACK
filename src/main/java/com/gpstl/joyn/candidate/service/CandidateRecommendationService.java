package com.gpstl.joyn.candidate.service;

import com.gpstl.joyn.application.exception.ApplicationException;
import com.gpstl.joyn.candidate.dto.CandidateRecommendationItem;
import com.gpstl.joyn.candidate.dto.CandidateRecommendationPageResponse;
import com.gpstl.joyn.candidate.model.Candidate;
import com.gpstl.joyn.candidate.model.CandidateEducation;
import com.gpstl.joyn.candidate.model.CandidateRecommendationScore;
import com.gpstl.joyn.candidate.model.CandidateSkill;
import com.gpstl.joyn.candidate.model.Degree;
import com.gpstl.joyn.candidate.model.CandidateRecommendationScore.Breakdown;
import com.gpstl.joyn.candidate.repository.CandidateRecommendationScoreRepository;
import com.gpstl.joyn.candidate.repository.CandidateRepository;
import com.gpstl.joyn.candidate.repository.RecommendationOfferRepository;
import com.gpstl.joyn.common.model.User;
import com.gpstl.joyn.common.repository.UserRepository;
import com.gpstl.joyn.joboffer.model.ExperienceLevel;
import com.gpstl.joyn.joboffer.model.JobOffer;
import com.gpstl.joyn.joboffer.model.RemoteMode;
import com.gpstl.joyn.joboffer.repository.ContractTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class CandidateRecommendationService {

    // Taille maximale pour limiter le coût des requêtes et du calcul des scores.
    private static final int MAX_PAGE_SIZE = 50;

    // Pondérations relatives des critères ; les poids des critères absents sont exclus du calcul final.
    private static final int SKILL_WEIGHT = 40;
    private static final int EXPERIENCE_WEIGHT = 25;
    private static final int EDUCATION_WEIGHT = 15;
    private static final int LOCATION_WEIGHT = 12;
    private static final int AVAILABILITY_WEIGHT = 8;

    // Plus grande distance théorique entre deux points de la surface terrestre, en kilomètres.
    private static final double MAX_DISTANCE_KM = 20_015.1;

    private final CandidateRepository candidateRepository;
    private final UserRepository userRepository;
    private final ContractTypeRepository contractTypeRepository;
    private final RecommendationOfferRepository offerRepository;
    private final CandidateRecommendationScoreRepository scoreRepository;

    public CandidateRecommendationService(
            CandidateRepository candidateRepository,
            UserRepository userRepository,
            ContractTypeRepository contractTypeRepository,
            RecommendationOfferRepository offerRepository,
            CandidateRecommendationScoreRepository scoreRepository) {
        this.candidateRepository = candidateRepository;
        this.userRepository = userRepository;
        this.contractTypeRepository = contractTypeRepository;
        this.offerRepository = offerRepository;
        this.scoreRepository = scoreRepository;
    }

    /**
     * Construit la page des offres recommandées pour l'utilisateur identifié par son JWT.
     * Vérifie son compte, applique les filtres métier, recalcule et persiste les scores,
     * puis trie les résultats avant de former la page demandée.
     */
    @Transactional
    public CandidateRecommendationPageResponse findRecommendations(
            Long userId,
            int page,
            int limit,
            String contractType,
            String rhythmType,
            String remote,
            String location,
            Double maxDistanceKm) {
        // 1. Vérifier l'existence, l'état et le rôle du compte. userId vient du sujet JWT,
        // jamais d'un paramètre transmis par le client.
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApplicationException.unauthorized("Authenticated user does not exist"));
        if (!"ACTIVE".equals(user.getStatus())) {
            throw ApplicationException.forbidden("Account is suspended");
        }
        if (!"CANDIDATE".equals(user.getRole())) {
            throw ApplicationException.forbidden("Only candidates can view recommendations");
        }
        if (page < 1) {
            throw ApplicationException.badRequest("Page must be greater than or equal to 1");
        }
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw ApplicationException.badRequest("Limit must be between 1 and " + MAX_PAGE_SIZE);
        }

        // 2. Charger le profil utilisé pour le matching et normaliser/valider les filtres.
        Candidate candidate = candidateRepository.findProfileByUserId(userId)
                .orElseThrow(() -> ApplicationException.forbidden("Candidate profile is required"));
        String normalizedContractType = normalizeFilter(contractType);
        if (normalizedContractType != null
                && contractTypeRepository.findByCodeIgnoreCase(normalizedContractType).isEmpty()) {
            throw ApplicationException.badRequest("Unknown contract_type");
        }
        String normalizedRhythm = normalizeFilter(rhythmType);
        String normalizedLocation = normalizeFilter(location);
        String remoteMode = parseRemoteMode(remote);
        validateMaxDistance(maxDistanceKm);

        // 3. La requête SQL conserve uniquement les offres publiées et disponibles qui n'ont
        // pas encore été swipées ou postulées par ce candidat.
        List<Long> offerIds = offerRepository.findEligibleOfferIds(
                candidate.getId(),
                normalizedContractType, 
                normalizedRhythm, 
                remoteMode, 
                normalizedLocation);
        if (offerIds.isEmpty()) {
            return page(List.of(), page, limit);
        }

        // 4. Charger les entités complètes (compétences et contrat compris), puis appliquer
        // le rayon maximal. Une offre à distance n'est pas limitée par une distance physique.
        Map<Long, JobOffer> offersById = new HashMap<>();
        offerRepository.findDistinctByIdIn(offerIds).forEach(offer -> offersById.put(offer.getId(), offer));
        List<JobOffer> offers = offerIds.stream()
                .map(offersById::get)
                .filter(java.util.Objects::nonNull)
                .filter(offer -> maxDistanceKm == null
                        || offer.getRemoteMode() == RemoteMode.REMOTE
                        || distanceKm(candidate, offer)
                                .filter(distance -> distance <= maxDistanceKm)
                                .isPresent())
                .toList();

        // 5. Récupérer les scores déjà enregistrés afin de les mettre à jour plutôt que de
        // créer un second enregistrement pour le même candidat et la même offre.
        Map<Long, CandidateRecommendationScore> scoresByOffer = new HashMap<>();
        scoreRepository.findAllByCandidate_IdAndJobOffer_IdIn(candidate.getId(), offerIds)
                .forEach(score -> scoresByOffer.put(score.getJobOffer().getId(), score));

        List<RankedRecommendation> ranked = new ArrayList<>(offers.size());
        List<CandidateRecommendationScore> scoresToSave = new ArrayList<>(offers.size());
        for (JobOffer offer : offers) {
            // Le score est toujours recalculé à partir du profil et de l'offre actuels.
            Breakdown breakdown = calculate(candidate, offer);
            CandidateRecommendationScore score = scoresByOffer.get(offer.getId());
            if (score == null) {
                score = new CandidateRecommendationScore(candidate, offer, breakdown);
            } else {
                score.update(breakdown);
            }
            scoresToSave.add(score);
            ranked.add(new RankedRecommendation(offer, score));
        }
        // Persister les nouveaux scores et actualiser les scores déjà calculés.
        scoreRepository.saveAll(scoresToSave);

        // 6. À score égal, les offres les plus récentes passent en premier ; l'id fournit
        // un ordre stable si deux dates de publication sont identiques ou absentes.
        ranked.sort(Comparator
                .comparingInt((RankedRecommendation result) -> result.score().getMatchScore()).reversed()
                .thenComparing(result -> result.offer().getPublishedAt(),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(result -> result.offer().getId(), Comparator.reverseOrder()));
        List<CandidateRecommendationItem> items = ranked.stream()
                .map(result -> CandidateRecommendationItem.from(result.offer(), result.score()))
                .toList();
        // 7. Paginer les résultats triés et renvoyer les métadonnées de pagination.
        return page(items, page, limit);
    }

    /** Découpe une liste déjà triée et calcule les métadonnées de la page, numérotée à partir de 1. */
    private static CandidateRecommendationPageResponse page(
            List<CandidateRecommendationItem> items, 
            int page, 
            int limit) {
        int total = items.size();
        long offset = (long) (page - 1) * limit;
        // Une page située au-delà du dernier résultat est vide, sans tentative de sous-liste invalide.
        List<CandidateRecommendationItem> content = offset >= total
                ? List.of()
                : items.subList((int) offset, Math.min((int) offset + limit, total));
        return new CandidateRecommendationPageResponse(
                content,
                page,
                limit,
                total,
                (int) Math.ceil((double) total / limit),
                offset + limit < total);
    }

    /**
     * Calcule chaque critère disponible puis normalise la moyenne pondérée :
     * un critère non renseigné n'ajoute ni points ni poids au dénominateur.
     */
    private static Breakdown calculate(Candidate candidate, JobOffer offer) {
        // Le score de compétences mesure la part des compétences exigées par l'offre
        // présentes dans le profil du candidat.
        Set<Long> candidateSkills = new HashSet<>();
        for (CandidateSkill skill : candidate.getSkills()) {
            candidateSkills.add(skill.getSkill().getId());
        }
        Integer skills = offer.getSkills().isEmpty()
                ? null
                : percentage(offer.getSkills().stream()
                        .filter(skill -> candidateSkills.contains(skill.getId()))
                        .count(), offer.getSkills().size());

        Integer experience = experienceScore(
            candidate.getYearsOfExperience(), 
            offer.getExperienceLevelRequired());
        Integer education = educationScore(candidate, offer.getDegreeLevelRequired());
        Double distance = distanceKm(candidate, offer).orElse(null);
        Integer location = locationScore(distance, offer.getRemoteMode());
        Integer availability = availabilityScore(candidate.getAvailableFrom(), offer.getStartDate());

        // Renormaliser les poids évite de pénaliser une offre parce que certaines données
        // facultatives du candidat ou de l'offre sont absentes.
        int totalWeight = 0;
        int weightedScore = 0;
        int[] weights = {
            SKILL_WEIGHT, 
            EXPERIENCE_WEIGHT, 
            EDUCATION_WEIGHT, 
            LOCATION_WEIGHT,
            AVAILABILITY_WEIGHT};
        Integer[] scores = {
            skills, 
            experience, 
            education, 
            location, 
            availability};
        for (int i = 0; i < scores.length; i++) {
            if (scores[i] != null) {
                totalWeight += weights[i];
                weightedScore += weights[i] * scores[i];
            }
        }
        int total = totalWeight == 0 ? 0 : (int) Math.round((double) weightedScore / totalWeight);
        return new Breakdown(
            total, 
            skills, 
            experience, 
            education, 
            location, 
            availability, 
            distance);
    }

    /** Compare l'expérience du candidat à une référence en années définie par le niveau demandé. */
    private static Integer experienceScore(Short candidateYears, ExperienceLevel requiredLevel) {
        if (candidateYears == null || requiredLevel == null) {
            return null;
        }
        int requiredYears = switch (requiredLevel) {
            case JUNIOR -> 0;
            case MID -> 3;
            case SENIOR -> 5;
        };
        return requiredYears == 0 ? 100 : (int) Math.min(100, candidateYears * 100L / requiredYears);
    }

    /** Compare le niveau d'études du candidat au niveau BAC demandé par l'offre. */
    private static Integer educationScore(Candidate candidate, String degreeLevelRequired) {
        if (degreeLevelRequired == null 
            || degreeLevelRequired.isBlank() 
            || candidate.getEducations().isEmpty()) {
            return null;
        }
        int requiredLevel = parsePostbacLevel(degreeLevelRequired);
        if (requiredLevel == 0) {
            return null;
        }
        int candidateLevel = candidate.getEducations().stream()
                .mapToInt(CandidateRecommendationService::educationLevel)
                .max()
                .orElse(0);
        return (int) Math.min(100, candidateLevel * 100L / requiredLevel);
    }

    /** Convertit une formation en niveau post-bac, en privilégiant l'année explicitement renseignée. */
    private static int educationLevel(CandidateEducation education) {
        if (education.getPostbacYear() != null) {
            return parsePostbacLevel(education.getPostbacYear().name());
        }
        Degree degree = education.getDegree();
        if (degree == null) {
            return 0;
        }
        return switch (degree) {
            case BACHELORS -> 3;
            case MASTERS, ENGINEERING, DOCTORATE -> 5;
            case OTHER -> 0;
        };
    }

    /** Extrait le numéro du niveau BAC_2 à BAC_5 ; zéro signifie une valeur non exploitable. */
    private static int parsePostbacLevel(String value) {
        if (value == null || !value.matches("BAC_[2-5]")) {
            return 0;
        }
        return value.charAt(value.length() - 1) - '0';
    }

    /** L'affinité géographique décroît avec la distance ; le travail entièrement à distance vaut 100 %. */
    private static Integer locationScore(Double distanceKm, RemoteMode remoteMode) {
        if (remoteMode == RemoteMode.REMOTE) {
            return 100;
        }
        return distanceKm == null ? null : (int) Math.round(Math.max(0, 100 - distanceKm));
    }

    /**
     * Calcule la distance orthodromique entre les coordonnées du candidat et de l'offre
     * avec la formule de Haversine. Renvoie une distance absente si une coordonnée manque.
     */
    private static java.util.Optional<Double> distanceKm(Candidate candidate, JobOffer offer) {
        if (candidate.getLatitude() == null || candidate.getLongitude() == null
                || offer.getLatitude() == null || offer.getLongitude() == null) {
            return java.util.Optional.empty();
        }
        double latitudeDelta = Math.toRadians(offer.getLatitude() - candidate.getLatitude());
        double longitudeDelta = Math.toRadians(offer.getLongitude() - candidate.getLongitude());
        double candidateLatitude = Math.toRadians(candidate.getLatitude());
        double offerLatitude = Math.toRadians(offer.getLatitude());
        double haversine = Math.pow(Math.sin(latitudeDelta / 2), 2)
                + Math.cos(candidateLatitude) * Math.cos(offerLatitude)
                * Math.pow(Math.sin(longitudeDelta / 2), 2);
        haversine = Math.clamp(haversine, 0, 1);
        double arc = 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
        return java.util.Optional.of(6371.0088 * arc);
    }

    /**
     * Évalue si le candidat sera disponible à la date de début : avant ou à la date prévue,
     * la correspondance est complète ; un retard important réduit progressivement le score.
     */
    private static Integer availabilityScore(LocalDate availableFrom, LocalDate offerStartDate) {
        if (availableFrom == null || offerStartDate == null) {
            return null;
        }
        if (!availableFrom.isAfter(offerStartDate)) {
            return 100;
        }
        long daysLate = ChronoUnit.DAYS.between(offerStartDate, availableFrom);
        if (daysLate <= 30) {
            return 80;
        }
        if (daysLate <= 90) {
            return 50;
        }
        return 0;
    }

    /** Convertit un ratio de correspondance en pourcentage entier. */
    private static Integer percentage(long numerator, long denominator) {
        return (int) Math.round((double) numerator * 100 / denominator);
    }

    /** Valide le mode de travail demandé en ignorant la casse et les espaces périphériques. */
    private static String parseRemoteMode(String remote) {
        String value = normalizeFilter(remote);
        if (value == null) {
            return null;
        }
        try {
            return RemoteMode.valueOf(value.toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException exception) {
            throw ApplicationException.badRequest("remote must be ONSITE, HYBRID, or REMOTE");
        }
    }

    /** Transforme une chaîne vide en filtre absent et retire les espaces périphériques. */
    private static String normalizeFilter(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Refuse les rayons négatifs, non finis ou supérieurs au diamètre terrestre maximal. */
    private static void validateMaxDistance(Double maxDistanceKm) {
        if (maxDistanceKm != null
                && (!Double.isFinite(maxDistanceKm) || maxDistanceKm < 0 || maxDistanceKm > MAX_DISTANCE_KM)) {
            throw ApplicationException.badRequest("max_distance_km must be between 0 and 20015.1");
        }
    }

    /** Associe une offre à son score pour conserver les deux ensemble pendant le tri. */
    private record RankedRecommendation(
        JobOffer offer, 
        CandidateRecommendationScore score) {}
}
