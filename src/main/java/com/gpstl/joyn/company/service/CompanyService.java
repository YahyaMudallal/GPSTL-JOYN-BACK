
package com.gpstl.joyn.company.service;

import com.gpstl.joyn.recruiter.repository.RecruiterRepository;
import com.gpstl.joyn.company.dto.CompanyResponse;
import com.gpstl.joyn.company.dto.CompanyStatusUpdateRequest;
import com.gpstl.joyn.company.model.Company;
import com.gpstl.joyn.company.repository.CompanyRepository;
import com.gpstl.joyn.joboffer.model.OfferStatus;
import com.gpstl.joyn.joboffer.repository.JobOfferRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final JobOfferRepository jobOfferRepository;
    private final RecruiterRepository recruiterProfileRepository;

    public CompanyService(
            CompanyRepository companyRepository,
            JobOfferRepository jobOfferRepository,
            RecruiterRepository recruiterProfileRepository) {
        this.companyRepository = companyRepository;
        this.jobOfferRepository = jobOfferRepository;
        this.recruiterProfileRepository = recruiterProfileRepository;
    }

    public CompanyResponse getProfile(
            Long companyId, boolean includeJobs, Authentication authentication) {

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

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
                company.getLogoUrl(),
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

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entreprise introuvable."));

        company.setVerificationStatus(normalizedStatus);
        Company updated = companyRepository.save(company);

        return new CompanyResponse(
                updated.getId(),
                updated.getName(),
                updated.getLogoUrl(),
                updated.getSector(),
                updated.getCompanySize(),
                updated.getWebsite(),
                updated.getDescription(),
                updated.getAddress(),
                0,
                List.of()
        );
    }
}


