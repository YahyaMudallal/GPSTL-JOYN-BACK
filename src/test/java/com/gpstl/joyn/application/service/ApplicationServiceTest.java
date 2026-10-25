package com.gpstl.joyn.application.service;

import com.gpstl.joyn.application.dto.CreateApplicationRequest;
import com.gpstl.joyn.application.dto.UpdateApplicationStatusRequest;
import com.gpstl.joyn.application.exception.ApplicationException;
import com.gpstl.joyn.application.model.Application;
import com.gpstl.joyn.application.model.ApplicationStatus;
import com.gpstl.joyn.application.repository.ApplicationRepository;
import com.gpstl.joyn.application.repository.ApplicationStatusRepository;
import com.gpstl.joyn.candidate.model.Candidate;
import com.gpstl.joyn.candidate.repository.CandidateRepository;
import com.gpstl.joyn.common.model.User;
import com.gpstl.joyn.common.repository.UserRepository;
import com.gpstl.joyn.joboffer.exception.JobApiException;
import com.gpstl.joyn.joboffer.model.JobOffer;
import com.gpstl.joyn.joboffer.repository.JobOfferRepository;
import com.gpstl.joyn.joboffer.service.JobAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApplicationServiceTest {

    private ApplicationRepository applicationRepository;
    private ApplicationStatusRepository statusRepository;
    private CandidateRepository candidateRepository;
    private UserRepository userRepository;
    private JobOfferRepository jobOfferRepository;
    private JobAccessService jobAccessService;
    private ApplicationService service;

    @BeforeEach
    void setUp() {
        applicationRepository = mock(ApplicationRepository.class);
        statusRepository = mock(ApplicationStatusRepository.class);
        candidateRepository = mock(CandidateRepository.class);
        userRepository = mock(UserRepository.class);
        jobOfferRepository = mock(JobOfferRepository.class);
        jobAccessService = mock(JobAccessService.class);
        service = new ApplicationService(applicationRepository, statusRepository,
                candidateRepository, userRepository, jobOfferRepository, jobAccessService);
    }

    @Test
    void createsApplicationWithPendingStatus() {
        Candidate candidate = mock(Candidate.class);
        User user = mock(User.class);
        JobOffer jobOffer = mock(JobOffer.class);
        ApplicationStatus pending = mock(ApplicationStatus.class);
        when(candidate.getId()).thenReturn(12L);
        when(jobOffer.getId()).thenReturn(34L);
        when(user.getRole()).thenReturn("CANDIDATE");
        when(user.getStatus()).thenReturn("ACTIVE");
        when(pending.getCode()).thenReturn("PENDING");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(candidateRepository.findByUserId(1L)).thenReturn(Optional.of(candidate));
        when(jobOfferRepository.findActiveByIdForUpdate(34L)).thenReturn(Optional.of(jobOffer));
        when(jobOffer.getStatus()).thenReturn(com.gpstl.joyn.joboffer.model.OfferStatus.PUBLISHED);
        when(applicationRepository.existsByCandidate_IdAndJobOffer_Id(12L, 34L)).thenReturn(false);
        when(statusRepository.findByCode("PENDING")).thenReturn(Optional.of(pending));
        when(applicationRepository.saveAndFlush(any(Application.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(1L, new CreateApplicationRequest(34L, "Hello", null));

        assertThat(response.candidateId()).isEqualTo(12L);
        assertThat(response.jobOfferId()).isEqualTo(34L);
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.coverMessage()).isEqualTo("Hello");
    }

    @Test
    void rejectsDuplicateApplication() {
        User user = mock(User.class);
        Candidate candidate = mock(Candidate.class);
        JobOffer jobOffer = mock(JobOffer.class);
        when(user.getRole()).thenReturn("CANDIDATE");
        when(user.getStatus()).thenReturn("ACTIVE");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(candidate.getId()).thenReturn(12L);
        when(candidateRepository.findByUserId(1L)).thenReturn(Optional.of(candidate));
        when(jobOfferRepository.findActiveByIdForUpdate(34L)).thenReturn(Optional.of(jobOffer));
        when(jobOffer.getId()).thenReturn(34L);
        when(jobOffer.getStatus()).thenReturn(com.gpstl.joyn.joboffer.model.OfferStatus.PUBLISHED);
        when(applicationRepository.existsByCandidate_IdAndJobOffer_Id(12L, 34L)).thenReturn(true);

        assertThatThrownBy(() -> service.create(1L, new CreateApplicationRequest(34L, null, null)))
                .isInstanceOf(ApplicationException.class)
                .extracting(exception -> ((ApplicationException) exception).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void recordsFirstTimeApplicationIsViewed() {
        User user = mock(User.class);
        Candidate candidate = mock(Candidate.class);
        JobOffer jobOffer = mock(JobOffer.class);
        ApplicationStatus pending = mock(ApplicationStatus.class);
        ApplicationStatus viewed = mock(ApplicationStatus.class);
        Application application = new Application(candidate, jobOffer, pending, null, null);
        when(candidate.getId()).thenReturn(12L);
        when(jobOffer.getId()).thenReturn(34L);
        when(user.getStatus()).thenReturn("ACTIVE");
        when(user.getRole()).thenReturn("RECRUITER");
        when(viewed.getCode()).thenReturn("VIEWED");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(applicationRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(application));
        when(jobAccessService.authorizedJob(1L, 34L)).thenReturn(jobOffer);
        when(statusRepository.findByCode("VIEWED")).thenReturn(Optional.of(viewed));
        when(applicationRepository.saveAndFlush(any(Application.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateStatus(1L, 7L, new UpdateApplicationStatusRequest("VIEWED"));

        assertThat(response.status()).isEqualTo("VIEWED");
        assertThat(application.getViewedAt()).isNotNull();
    }

    @Test
    void rejectsInactiveUserBeforeCreatingApplication() {
        User suspended = mock(User.class);
        when(suspended.getStatus()).thenReturn("SUSPENDED");
        when(userRepository.findById(1L)).thenReturn(Optional.of(suspended));

        assertThatThrownBy(() -> service.create(1L, new CreateApplicationRequest(34L, null, null)))
                .isInstanceOf(ApplicationException.class)
                .extracting(exception -> ((ApplicationException) exception).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deniesStatusUpdatesWhenJobAccessIsRejected() {
        User user = mock(User.class);
        Candidate candidate = mock(Candidate.class);
        JobOffer jobOffer = mock(JobOffer.class);
        Application application = new Application(candidate, jobOffer, mock(ApplicationStatus.class), null, null);
        when(user.getStatus()).thenReturn("ACTIVE");
        when(user.getRole()).thenReturn("RECRUITER");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(applicationRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(application));
        when(jobOffer.getId()).thenReturn(34L);
        when(jobAccessService.authorizedJob(1L, 34L))
                .thenThrow(new JobApiException(403, "Job belongs to another company"));

        assertThatThrownBy(() -> service.updateStatus(1L, 7L, new UpdateApplicationStatusRequest("VIEWED")))
                .isInstanceOf(ApplicationException.class)
                .extracting(exception -> ((ApplicationException) exception).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void reportsMissingInitialStatusAsAnInternalConfigurationError() {
        User user = mock(User.class);
        Candidate candidate = mock(Candidate.class);
        JobOffer jobOffer = mock(JobOffer.class);
        when(user.getRole()).thenReturn("CANDIDATE");
        when(user.getStatus()).thenReturn("ACTIVE");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(candidate.getId()).thenReturn(12L);
        when(candidateRepository.findByUserId(1L)).thenReturn(Optional.of(candidate));
        when(jobOfferRepository.findActiveByIdForUpdate(34L)).thenReturn(Optional.of(jobOffer));
        when(jobOffer.getId()).thenReturn(34L);
        when(jobOffer.getStatus()).thenReturn(com.gpstl.joyn.joboffer.model.OfferStatus.PUBLISHED);
        when(applicationRepository.existsByCandidate_IdAndJobOffer_Id(12L, 34L)).thenReturn(false);
        when(statusRepository.findByCode("PENDING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(1L, new CreateApplicationRequest(34L, null, null)))
                .isInstanceOf(ApplicationException.class)
                .extracting(exception -> ((ApplicationException) exception).getStatus())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
