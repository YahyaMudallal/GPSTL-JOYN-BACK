
package com.gpstl.joyn.company.service;

import com.gpstl.joyn.recruiter.repository.RecruiterRepository;
import com.gpstl.joyn.company.dto.CompanyResponse;
import com.gpstl.joyn.company.dto.CompanyStatusUpdateRequest;
import com.gpstl.joyn.company.model.Company;
import com.gpstl.joyn.company.repository.CompanyRepository;
import com.gpstl.joyn.joboffer.model.JobOffer;
import com.gpstl.joyn.joboffer.model.JobOfferAudit;
import com.gpstl.joyn.joboffer.model.OfferStatus;
import com.gpstl.joyn.joboffer.repository.JobOfferAuditRepository;
import com.gpstl.joyn.joboffer.repository.JobOfferRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final JobOfferRepository jobOfferRepository;
    private final RecruiterRepository recruiterProfileRepository;
    private final JobOfferAuditRepository jobOfferAuditRepository;
    private final CompanyLogoStorage logoStorage;

    public CompanyService(
            CompanyRepository companyRepository,
            JobOfferRepository jobOfferRepository,
            RecruiterRepository recruiterProfileRepository,
            JobOfferAuditRepository jobOfferAuditRepository,
            CompanyLogoStorage logoStorage) {
        this.companyRepository = companyRepository;
        this.jobOfferRepository = jobOfferRepository;
        this.recruiterProfileRepository = recruiterProfileRepository;
        this.jobOfferAuditRepository = jobOfferAuditRepository;
        this.logoStorage = logoStorage;
  
    }

    public CompanyResponse getProfile(
            Long companyId, boolean includeJobs, Authentication authentication) {

        Company company = findActiveCompany(companyId);

        boolean allowed = "VERIFIED".equals(company.getVerificationStatus());

        if (!allowed && authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Jwt jwt) {

            String role = jwt.getClaimAsString("role");

            if ("ADMIN".equals(role)) {
                allowed = true;
            } else if ("RECRUITER".equals(role)) {
                try {
                    Long userId = Long.valueOf(jwt.getSubject());

                    allowed = recruiterProfileRepository.findByUserId(userId)
                            .map(profile -> profile.getCompany().getId().equals(companyId))
                            .orElse(false);
                } catch (NumberFormatException ignored) {
                    allowed = false;
                }
            }
        }

        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        var activeOffers = jobOfferRepository.findPublicOffersByCompanyId(companyId, OfferStatus.PUBLISHED);

        List<CompanyResponse.ActiveJob> jobs = includeJobs
                ? activeOffers.stream()
                    .map(job -> new CompanyResponse.ActiveJob(
                            job.getId(),
                            job.getTitle(),
                            job.getContractType().getCode(),
                            job.getLocation(),
                            job.getRemoteMode().name(),
                            job.getPublishedAt()
                    ))
                    .toList()
                : List.of();

        return new CompanyResponse(
                company.getId(),
                company.getName(),
                logoStorage.createPresignedUrlIfValid(company.getLogoUrl()), // R84 : clé -> URL
                company.getSector(),
                company.getCompanySize(),
                company.getWebsite(),
                company.getDescription(),
                company.getAddress(),
                activeOffers.size(),
                jobs
        );
    }

    private static final java.util.Set<String> ALLOWED_STATUSES = 
        java.util.Set.of("NOT_VERIFIED", "VERIFIED", "REJECTED");

    @org.springframework.transaction.annotation.Transactional
    public CompanyResponse updateVerificationStatus(
            Long companyId,
            CompanyStatusUpdateRequest request,
            Authentication authentication) {

        // Contrôle d'accès ADMIN via JWT
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)
                || !"ADMIN".equals(jwt.getClaimAsString("role"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès réservé aux administrateurs.");
        }

        // Validation du payload
        if (request == null || request.verificationStatus() == null || request.verificationStatus().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le statut de vérification est requis.");
        }

        String normalizedStatus = request.verificationStatus().trim().toUpperCase();
        if (!ALLOWED_STATUSES.contains(normalizedStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Statut invalide. Valeurs acceptées : NOT_VERIFIED, VERIFIED, REJECTED."
            );
        }

        Company company = findActiveCompany(companyId);

        company.setVerificationStatus(normalizedStatus);
        Company updated = companyRepository.save(company);

        return new CompanyResponse(
                updated.getId(),
                updated.getName(),
                logoStorage.createPresignedUrlIfValid(updated.getLogoUrl()), // R84 : clé -> URL
                updated.getSector(),
                updated.getCompanySize(),
                updated.getWebsite(),
                updated.getDescription(),
                updated.getAddress(),
                0,
                List.of()
        );
    }

    @Transactional
    public CompanyResponse updateProfile(
            Long companyId,
            java.util.Map<String, Object> changes,
            Authentication authentication) {

        //  Vérification du JWT
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification requise.");
        }

        String role = jwt.getClaimAsString("role");

        // Gestion des rôles : CANDIDATE bloqué, ADMIN autorisé d'office, RECRUITER sous condition
        if ("ADMIN".equals(role)) {
            // L'administrateur a tous les droits
        } else if ("RECRUITER".equals(role)) {
            Long userId;
            try {
                userId = Long.valueOf(jwt.getSubject());
            } catch (NumberFormatException e) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identifiant utilisateur invalide.");
            }

            boolean belongsToCompany = recruiterProfileRepository.findByUserId(userId)
                    .map(recruiter -> recruiter.getCompany().getId().equals(companyId))
                    .orElse(false);

            if (!belongsToCompany) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vous n'appartenez pas à cette entreprise.");
            }
        } else {
            // Rôle CANDIDATE ou inconnu
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès interdit pour ce rôle.");
        }
        // Vérification de l'existence de l'entreprise
        Company company = findActiveCompany(companyId);

        // Validation des changements
        if (changes == null || changes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucune modification fournie.");
        }

        java.util.Set<String> allowedFields = java.util.Set.of(
                "name", "description", "website", "logoUrl",
                "sector", "companySize", "address");

        for (String field : changes.keySet()) {
            if (!allowedFields.contains(field)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Champ non modifiable : " + field);
            }
            Object value = changes.get(field);
            if (value != null && !(value instanceof String)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Valeur invalide pour le champ : " + field);
            }
        }

        if (changes.containsKey("name")) {
            String name = (String) changes.get("name");
            if (name == null || name.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Le nom est obligatoire.");
            }
            company.setName(name);
        }

        if (changes.containsKey("description"))
            company.setDescription((String) changes.get("description"));
        if (changes.containsKey("website"))
            company.setWebsite((String) changes.get("website"));
        if (changes.containsKey("logoUrl"))
            company.setLogoUrl((String) changes.get("logoUrl"));
        if (changes.containsKey("sector"))
            company.setSector((String) changes.get("sector"));
        if (changes.containsKey("companySize"))
            company.setCompanySize((String) changes.get("companySize"));
        if (changes.containsKey("address"))
            company.setAddress((String) changes.get("address"));

        companyRepository.save(company);

        return getProfile(companyId, true, authentication);
    }

    /**
     * Suppression logique d'une entreprise.
     * Dans une seule transaction : l'entreprise passe en DELETED, ses offres non clôturées passent
     * en CLOSED (retirées du deck et des recommandations) et ses recruteurs perdent leurs droits,
     * car tous les contrôles d'accès recruteur refusent une entreprise supprimée.
     * Aucune candidature ni aucun message n'est supprimé.
     */
    @Transactional
    public void deleteCompany(Long companyId, String reason, Authentication authentication) {

        // Vérification du JWT
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification requise.");
        }

        Long userId;
        try {
            userId = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identifiant utilisateur invalide.");
        }

        // ADMIN : toutes les entreprises ; RECRUITER : uniquement la sienne
        String role = jwt.getClaimAsString("role");
        if ("RECRUITER".equals(role)) {
            boolean belongsToCompany = recruiterProfileRepository.findByUserId(userId)
                    .map(recruiter -> recruiter.getCompany().getId().equals(companyId))
                    .orElse(false);

            if (!belongsToCompany) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vous n'appartenez pas à cette entreprise.");
            }
        } else if (!"ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès interdit pour ce rôle.");
        }

        // Inexistante ou déjà supprimée : 404
        Company company = findActiveCompany(companyId);

        Instant now = Instant.now();
        company.markDeleted(now);
        companyRepository.save(company);

        // Clôture des offres encore actives, comme une clôture manuelle (JobClosureService)
        String auditReason = (reason == null || reason.isBlank())
                ? "Entreprise supprimée"
                : "Entreprise supprimée : " + reason;

        List<JobOffer> offers = jobOfferRepository.findByCompanyIdAndDeletedAtIsNull(companyId);
        for (JobOffer offer : offers) {
            offer.setStatus(OfferStatus.CLOSED);
            offer.setDeletedAt(now.atOffset(ZoneOffset.UTC));
        }
        jobOfferRepository.saveAll(offers);
        jobOfferAuditRepository.saveAll(offers.stream()
                .map(offer -> new JobOfferAudit(userId, offer, "CLOSE", auditReason))
                .toList());
    }

    /** Retrouve une entreprise non supprimée, sinon 404. */
    private Company findActiveCompany(Long companyId) {
        return companyRepository.findById(companyId)
                .filter(company -> !company.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entreprise introuvable."));
    }
}
