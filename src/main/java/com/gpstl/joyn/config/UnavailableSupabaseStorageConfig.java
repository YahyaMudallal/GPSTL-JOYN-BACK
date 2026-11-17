package com.gpstl.joyn.config;

import com.gpstl.joyn.candidate.service.CandidateResumeStorage;
import com.gpstl.joyn.candidate.service.UnavailableCandidateResumeStorage;
import com.gpstl.joyn.company.service.CompanyLogoStorage;
import com.gpstl.joyn.company.service.UnavailableCompanyLogoStorage;
import com.gpstl.joyn.user.service.UnavailableUserProfilePictureStorage;
import com.gpstl.joyn.user.service.UserProfilePictureStorage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
@Conditional(SupabaseStorageUnavailableCondition.class)
public class UnavailableSupabaseStorageConfig {

    @Bean
    public CandidateResumeStorage unavailableCandidateResumeStorage() {
        return new UnavailableCandidateResumeStorage();
    }

    @Bean
    public UserProfilePictureStorage unavailableUserProfilePictureStorage() {
        return new UnavailableUserProfilePictureStorage();
    }

    @Bean
    public CompanyLogoStorage unavailableCompanyLogoStorage() {
        return new UnavailableCompanyLogoStorage();
    }
}
