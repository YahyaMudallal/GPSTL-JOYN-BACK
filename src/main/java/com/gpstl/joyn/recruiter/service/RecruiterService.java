package com.gpstl.joyn.recruiter.service;

import com.gpstl.joyn.common.model.User;
import com.gpstl.joyn.common.service.UserAccountService;
import com.gpstl.joyn.company.model.Company;
import com.gpstl.joyn.company.repository.CompanyRepository;
import com.gpstl.joyn.recruiter.dto.RecruiterRegisterRequest;
import com.gpstl.joyn.recruiter.dto.RecruiterRegisterResponse;
import com.gpstl.joyn.recruiter.model.Recruiter;
import com.gpstl.joyn.recruiter.repository.RecruiterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecruiterService {

    private final UserAccountService userAccountService;
    private final CompanyRepository companyRepository;
    private final RecruiterRepository recruiterProfileRepository;

    public RecruiterService(
            UserAccountService userAccountService,
            CompanyRepository companyRepository,
            RecruiterRepository recruiterProfileRepository) {
        this.userAccountService = userAccountService;
        this.companyRepository = companyRepository;
        this.recruiterProfileRepository = recruiterProfileRepository;
    }

    @Transactional
    public RecruiterRegisterResponse register(RecruiterRegisterRequest request) {
        validateProfileInfo(request);

        User user = userAccountService.createUserAccount(
                request.email(),
                request.password(),
                "RECRUITER"
        );

        String companyName = request.companyName().trim();
        Company company = companyRepository.findByNameIgnoreCase(companyName)
                .orElseGet(() -> companyRepository.save(new Company(companyName)));

        Recruiter profile = new Recruiter(
                user,
                company,
                request.firstName().trim(),
                request.lastName().trim()
        );
        Recruiter savedProfile = recruiterProfileRepository.save(profile);

        return new RecruiterRegisterResponse(
                savedProfile.getId(),
                user.getId(),
                user.getEmail(),
                savedProfile.getFirstName(),
                savedProfile.getLastName(),
                company.getName()
        );
    }

    private void validateProfileInfo(RecruiterRegisterRequest request) {
        if (request == null
                || isBlank(request.firstName())
                || isBlank(request.lastName())
                || isBlank(request.companyName())) {
            throw new IllegalArgumentException("Les informations du profil recruteur sont incomplètes.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}