package com.gpstl.joyn.joboffer;

import com.gpstl.joyn.common.models.User;
import com.gpstl.joyn.common.repositories.UserRepository;
import com.gpstl.joyn.joboffer.exception.JobApiException;
import com.gpstl.joyn.joboffer.models.JobOffer;
import com.gpstl.joyn.joboffer.repositories.JobOfferRepository;
import com.gpstl.joyn.joboffer.service.JobAccessService;
import com.gpstl.joyn.recruiter.models.Company;
import com.gpstl.joyn.recruiter.models.RecruiterProfile;
import com.gpstl.joyn.recruiter.repositories.RecruiterProfileRepository;
import org.junit.jupiter.api.*;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JobAccessServiceTest {
    UserRepository users;
    RecruiterProfileRepository recruiters;
    JobOfferRepository offers;
    JobAccessService service;
    User user;
    JobOffer job;
    Company company;

    @BeforeEach void setup() {
        users = mock(UserRepository.class);
        recruiters = mock(RecruiterProfileRepository.class);
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
        RecruiterProfile recruiter = mock(RecruiterProfile.class);
        when(recruiter.getCompany()).thenReturn(company);
        when(recruiters.findByUserId(1L)).thenReturn(Optional.of(recruiter));
        when(user.getRole()).thenReturn("RECRUITER");
        assertThat(service.authorizedJob(1, 10)).isSameAs(job);
    }

    @Test void rejectsOtherCompanyMissingProfileCandidateAndSuspendedAccounts() {
        Company other = mock(Company.class);
        when(other.getId()).thenReturn(21L);
        RecruiterProfile recruiter = mock(RecruiterProfile.class);
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
