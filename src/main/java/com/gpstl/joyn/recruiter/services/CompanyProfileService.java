
package com.gpstl.joyn.recruiter.services;

import com.gpstl.joyn.recruiter.dto.CompanyProfileResponse;
import com.gpstl.joyn.recruiter.models.Company;
import com.gpstl.joyn.recruiter.repositories.CompanyRepository;
import com.gpstl.joyn.recruiter.repositories.RecruiterProfileRepository;
import com.gpstl.joyn.joboffer.models.OfferStatus;
import com.gpstl.joyn.joboffer.repositories.JobOfferRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CompanyProfileService {

    private final CompanyRepository companyRepository;
    private final JobOfferRepository jobOfferRepository;
    private final RecruiterProfileRepository recruiterProfileRepository;

    public CompanyProfileService(
            CompanyRepository companyRepository,
            JobOfferRepository jobOfferRepository,
            RecruiterProfileRepository recruiterProfileRepository) {
        this.companyRepository = companyRepository;
        this.jobOfferRepository = jobOfferRepository;
        this.recruiterProfileRepository = recruiterProfileRepository;
    }

    public CompanyProfileResponse getProfile(
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

        List<CompanyProfileResponse.ActiveJob> jobs = includeJobs
                ? activeOffers.stream()
                    .map(job -> new CompanyProfileResponse.ActiveJob(
                            job.getId(),
                            job.getTitle(),
                            job.getContractType().getCode(),
                            job.getLocation(),
                            job.getRemoteMode().name(),
                            job.getPublishedAt()
                    ))
                    .toList()
                : List.of();

        return new CompanyProfileResponse(
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
}


