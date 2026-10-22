package com.gpstl.joyn.joboffer.service;

import com.gpstl.joyn.common.model.User;
import com.gpstl.joyn.common.repository.UserRepository;
import com.gpstl.joyn.company.model.Company;
import com.gpstl.joyn.joboffer.exception.JobApiException;
import com.gpstl.joyn.joboffer.model.JobOffer;
import com.gpstl.joyn.joboffer.repository.JobOfferRepository;
import com.gpstl.joyn.recruiter.model.Recruiter;
import com.gpstl.joyn.recruiter.repository.RecruiterRepository;

import org.junit.jupiter.api.*;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Teste les règles de rôle et d’entreprise avec des repositories simulés, sans base de données. */
class JobAccessServiceTest {
    UserRepository users;
    RecruiterRepository recruiters;
    JobOfferRepository offers;
    JobAccessService service;
    User user;
    JobOffer job;
    Company company;

    @BeforeEach void setup() {
        users = mock(UserRepository.class);
        recruiters = mock(RecruiterRepository.class);
        offers = mock(JobOfferRepository.class);
        service = new JobAccessService(users, recruiters, offers);
        user = mock(User.class); job = mock(JobOffer.class); company = mock(Company.class);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(user.getStatus()).thenReturn("ACTIVE");
        when(company.getId()).thenReturn(20L);
        when(job.getCompany()).thenReturn(company);
        when(offers.findActiveByIdForUpdate(10L)).thenReturn(Optional.of(job));
    }

    @Test void allowsAdministratorAndSameCompanyRecruiter() {
        when(user.getRole()).thenReturn("ADMIN");
        assertThat(service.authorizedJob(1, 10)).isSameAs(job);
        Recruiter recruiter = mock(Recruiter.class);
        when(recruiter.getCompany()).thenReturn(company);
        when(recruiters.findByUserId(1L)).thenReturn(Optional.of(recruiter));
        when(user.getRole()).thenReturn("RECRUITER");
        assertThat(service.authorizedJob(1, 10)).isSameAs(job);
    }

    @Test void rejectsOtherCompanyMissingProfileCandidateAndSuspendedAccounts() {
        Company other = mock(Company.class);
        when(other.getId()).thenReturn(21L);
        Recruiter recruiter = mock(Recruiter.class);
        when(recruiter.getCompany()).thenReturn(other);
        when(recruiters.findByUserId(1L)).thenReturn(Optional.of(recruiter));
        when(user.getRole()).thenReturn("RECRUITER");
        assertForbidden();
        when(recruiters.findByUserId(1L)).thenReturn(Optional.empty());
        assertForbidden();
        when(user.getRole()).thenReturn("CANDIDATE");
        assertForbidden();
        when(user.getRole()).thenReturn("ADMIN");
        when(user.getStatus()).thenReturn("SUSPENDED");
        assertForbidden();
    }

    @Test void missingUserIsUnauthorizedAndMissingJobIsNotFound() {
        when(users.findById(1L)).thenReturn(Optional.empty());
        assertThat(catchThrowableOfType(() -> service.authorizedJob(1,10), JobApiException.class).getStatus()).isEqualTo(401);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(user.getRole()).thenReturn("ADMIN");
        when(offers.findActiveByIdForUpdate(10L)).thenReturn(Optional.empty());
        assertThat(catchThrowableOfType(() -> service.authorizedJob(1,10), JobApiException.class).getStatus()).isEqualTo(404);
    }

    private void assertForbidden() {
        assertThat(catchThrowableOfType(() -> service.authorizedJob(1,10), JobApiException.class).getStatus()).isEqualTo(403);
    }
}
