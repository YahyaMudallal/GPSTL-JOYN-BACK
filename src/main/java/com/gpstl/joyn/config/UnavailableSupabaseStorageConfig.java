package com.gpstl.joyn.config;

import com.gpstl.joyn.candidate.service.CandidateResumeStorage;
import com.gpstl.joyn.candidate.service.UnavailableCandidateResumeStorage;
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
}
