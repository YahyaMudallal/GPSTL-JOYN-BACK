package com.gpstl.joyn.joboffer;

import com.gpstl.joyn.joboffer.exception.JobApiException;
import com.gpstl.joyn.joboffer.repositories.JobAccessRepository;
import com.gpstl.joyn.joboffer.service.JobAccessService;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JobAccessServiceTest {
    JobAccessRepository repository;
    JobAccessService service;
    @BeforeEach void setup() {
        repository = mock(JobAccessRepository.class);
        service = new JobAccessService(repository);
        when(repository.lockActive(10)).thenReturn(Optional.of(Map.of("company_id", 20L)));
    }
    @Test void allowsAdministratorAndSameCompanyRecruiter() {
        when(repository.actor(1)).thenReturn(Optional.of(Map.of("role","ADMIN","status","ACTIVE")));
        assertThat(service.authorizedJob(1,10)).containsEntry("company_id",20L);
        when(repository.actor(1)).thenReturn(Optional.of(Map.of("role","RECRUITER","status","ACTIVE","company_id",20L)));
        assertThat(service.authorizedJob(1,10)).containsEntry("company_id",20L);
    }
    @Test void rejectsOtherCompanyCandidateAndSuspendedAccounts() {
        for (Map<String,Object> actor : List.<Map<String,Object>>of(
                Map.of("role","RECRUITER","status","ACTIVE","company_id",21L),
                Map.of("role","CANDIDATE","status","ACTIVE"),
                Map.of("role","ADMIN","status","SUSPENDED"))) {
            when(repository.actor(1)).thenReturn(Optional.of(actor));
            assertThat(catchThrowableOfType(() -> service.authorizedJob(1,10),JobApiException.class).getStatus()).isEqualTo(403);
        }
    }
    @Test void missingUserIsUnauthorizedAndMissingJobIsNotFound() {
        when(repository.actor(1)).thenReturn(Optional.empty());
        assertThat(catchThrowableOfType(() -> service.authorizedJob(1,10),JobApiException.class).getStatus()).isEqualTo(401);
        when(repository.actor(1)).thenReturn(Optional.of(Map.of("role","ADMIN","status","ACTIVE")));
        when(repository.lockActive(10)).thenReturn(Optional.empty());
        assertThat(catchThrowableOfType(() -> service.authorizedJob(1,10),JobApiException.class).getStatus()).isEqualTo(404);
    }
}
