package com.gpstl.joyn.application.service;

import com.gpstl.joyn.application.dto.CreateApplicationRequest;
import com.gpstl.joyn.application.dto.UpdateApplicationStatusRequest;
import com.gpstl.joyn.application.exception.ApplicationException;
import com.gpstl.joyn.application.model.Application;
import com.gpstl.joyn.application.model.ApplicationStatus;
import com.gpstl.joyn.application.repository.ApplicationRepository;
import com.gpstl.joyn.application.repository.ApplicationStatusRepository;
import com.gpstl.joyn.application.repository.CandidateApplicationHistoryProjection;
import com.gpstl.joyn.candidate.model.Candidate;
import com.gpstl.joyn.candidate.repository.CandidateRepository;
import com.gpstl.joyn.candidate.service.CandidateResumeStorage;
import com.gpstl.joyn.common.model.User;
import com.gpstl.joyn.common.repository.UserRepository;
import com.gpstl.joyn.joboffer.exception.JobApiException;
import com.gpstl.joyn.joboffer.model.JobOffer;
import com.gpstl.joyn.joboffer.repository.JobOfferRepository;
import com.gpstl.joyn.joboffer.service.JobAccessService;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicationServiceTest {

    private ApplicationRepository applicationRepository;
    private ApplicationStatusRepository statusRepository;
    private CandidateRepository candidateRepository;
    private UserRepository userRepository;
    private JobOfferRepository jobOfferRepository;
    private JobAccessService jobAccessService;
    private CandidateResumeStorage resumeStorage;
    private ApplicationService service;

    @BeforeEach
    void setUp() {
        applicationRepository = mock(ApplicationRepository.class);
        statusRepository = mock(ApplicationStatusRepository.class);
        candidateRepository = mock(CandidateRepository.class);
        userRepository = mock(UserRepository.class);
        jobOfferRepository = mock(JobOfferRepository.class);
        jobAccessService = mock(JobAccessService.class);
        resumeStorage = mock(CandidateResumeStorage.class);
        service = new ApplicationService(applicationRepository, statusRepository,
                candidateRepository, userRepository, jobOfferRepository, jobAccessService, resumeStorage);
    }

    @Test
    void createsApplicationWithPendingStatus() {
        Candidate candidate = mock(Candidate.class);
        User user = mock(User.class);
        JobOffer jobOffer = mock(JobOffer.class);
        ApplicationStatus pending = mock(ApplicationStatus.class);
        when(candidate.getId()).thenReturn(12L);
        String resumeKey = "resumes/12/123e4567-e89b-12d3-a456-426614174000.pdf";
        when(candidate.getResumeUrl()).thenReturn(resumeKey);
        when(resumeStorage.createPresignedUrl(resumeKey))
                .thenReturn("https://storage.example.test/signed?X-Amz-Expires=900");
        doCallRealMethod().when(resumeStorage).createPresignedUrlIfValid(resumeKey);
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
        assertThat(response.resumeUrl()).isEqualTo("https://storage.example.test/signed?X-Amz-Expires=900");
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
        when(statusRepository.findByCode("VIEWED")).thenReturn(Optional.of(viewed));
        when(applicationRepository.saveAndFlush(any(Application.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateStatus(1L, 7L, new UpdateApplicationStatusRequest("VIEWED"));

        assertThat(response.status()).isEqualTo("VIEWED");
        assertThat(application.getViewedAt()).isNotNull();
        // Company check on the application's job offer, without locking or excluding closed jobs.
        verify(jobAccessService).checkCompanyAccess(1L, jobOffer);
        verify(jobAccessService, never()).authorizedJob(anyLong(), anyLong());
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
        doThrow(new JobApiException(403, "Job belongs to another company"))
                .when(jobAccessService).checkCompanyAccess(1L, jobOffer);

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

    @Test
    void returnsMappedHistoryPageAndUsesAuthenticatedUserAndRequestedFilters() {
        User user = mock(User.class);
        Candidate candidate = mock(Candidate.class);
        CandidateApplicationHistoryProjection application = mock(CandidateApplicationHistoryProjection.class);
        Instant timestamp = Instant.parse("2026-10-24T14:03:10Z");
        when(user.getStatus()).thenReturn("ACTIVE");
        when(user.getRole()).thenReturn("CANDIDATE");
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(candidateRepository.findByUserId(9L)).thenReturn(Optional.of(candidate));
        when(application.getId()).thenReturn(52L);
        when(application.getJobOfferId()).thenReturn(34L);
        when(application.getJobTitle()).thenReturn("Software Engineer");
        when(application.getCompanyId()).thenReturn(12L);
        when(application.getCompanyName()).thenReturn("Example Company");
        when(application.getStatus()).thenReturn("PENDING");
        when(application.getViewedAt()).thenReturn(timestamp);
        when(application.getCoverMessage()).thenReturn("Please consider my application.");
        String resumeKey = "resumes/12/123e4567-e89b-12d3-a456-426614174000.pdf";
        when(application.getResumeUrl()).thenReturn(resumeKey);
        when(resumeStorage.createPresignedUrl(resumeKey))
                .thenReturn("https://storage.example.test/signed?X-Amz-Expires=900");
        doCallRealMethod().when(resumeStorage).createPresignedUrlIfValid(resumeKey);
        when(application.getCreatedAt()).thenReturn(timestamp);
        when(application.getUpdatedAt()).thenReturn(timestamp);
        when(application.getConversationId()).thenReturn(81L);
        when(applicationRepository.findCandidateHistory(9L, "PENDING", false, PageRequest.of(1, 10)))
                .thenReturn(new PageImpl<>(List.of(application), PageRequest.of(1, 10), 11));

        var result = service.findCandidateHistory(9L, 2, 10, "PENDING", "created_at,desc");

        assertThat(result.page()).isEqualTo(2);
        assertThat(result.limit()).isEqualTo(10);
        assertThat(result.totalElements()).isEqualTo(11);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst())
                .satisfies(item -> {
                    assertThat(item.id()).isEqualTo(52L);
                    assertThat(item.jobOfferId()).isEqualTo(34L);
                    assertThat(item.jobTitle()).isEqualTo("Software Engineer");
                    assertThat(item.companyId()).isEqualTo(12L);
                    assertThat(item.companyName()).isEqualTo("Example Company");
                    assertThat(item.status()).isEqualTo("PENDING");
                    assertThat(item.viewedAt()).isEqualTo(OffsetDateTime.ofInstant(timestamp, ZoneOffset.UTC));
                    assertThat(item.coverMessage()).isEqualTo("Please consider my application.");
                    assertThat(item.resumeUrl())
                            .isEqualTo("https://storage.example.test/signed?X-Amz-Expires=900");
                    assertThat(item.createdAt()).isEqualTo(OffsetDateTime.ofInstant(timestamp, ZoneOffset.UTC));
                    assertThat(item.updatedAt()).isEqualTo(OffsetDateTime.ofInstant(timestamp, ZoneOffset.UTC));
                    assertThat(item.conversationId()).isEqualTo(81L);
                });
        verify(applicationRepository).findCandidateHistory(
                9L, "PENDING", false, PageRequest.of(1, 10));
    }

    @Test
    void rejectsHistoryRequestsWhenUserIsNotAnActiveCandidateWithAProfile() {
        User recruiter = mock(User.class);
        when(recruiter.getStatus()).thenReturn("ACTIVE");
        when(recruiter.getRole()).thenReturn("RECRUITER");
        when(userRepository.findById(2L)).thenReturn(Optional.of(recruiter));

        assertHistoryError(2L, 1, 20, null, "created_at,desc", HttpStatus.FORBIDDEN);

        User suspendedCandidate = mock(User.class);
        when(suspendedCandidate.getStatus()).thenReturn("SUSPENDED");
        when(userRepository.findById(3L)).thenReturn(Optional.of(suspendedCandidate));
        assertHistoryError(3L, 1, 20, null, "created_at,desc", HttpStatus.FORBIDDEN);

        User candidateWithoutProfile = mock(User.class);
        when(candidateWithoutProfile.getStatus()).thenReturn("ACTIVE");
        when(candidateWithoutProfile.getRole()).thenReturn("CANDIDATE");
        when(userRepository.findById(4L)).thenReturn(Optional.of(candidateWithoutProfile));
        when(candidateRepository.findByUserId(4L)).thenReturn(Optional.empty());
        assertHistoryError(4L, 1, 20, null, "created_at,desc", HttpStatus.FORBIDDEN);

        when(userRepository.findById(5L)).thenReturn(Optional.empty());
        assertHistoryError(5L, 1, 20, null, "created_at,desc", HttpStatus.UNAUTHORIZED);
        verify(applicationRepository, never()).findCandidateHistory(
                any(), any(), anyBoolean(), any());
    }

    @Test
    void rejectsOutOfRangePaginationAndUnsupportedHistoryFiltersBeforeQuerying() {
        User user = mock(User.class);
        Candidate candidate = mock(Candidate.class);
        when(user.getStatus()).thenReturn("ACTIVE");
        when(user.getRole()).thenReturn("CANDIDATE");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(candidateRepository.findByUserId(1L)).thenReturn(Optional.of(candidate));

        assertHistoryError(1L, 0, 20, null, "created_at,desc", HttpStatus.BAD_REQUEST);
        assertHistoryError(1L, -1, 20, null, "created_at,desc", HttpStatus.BAD_REQUEST);
        assertHistoryError(1L, 1, 0, null, "created_at,desc", HttpStatus.BAD_REQUEST);
        assertHistoryError(1L, 1, 51, null, "created_at,desc", HttpStatus.BAD_REQUEST);
        assertHistoryError(1L, 1, 20, "UNKNOWN", "created_at,desc", HttpStatus.BAD_REQUEST);
        assertHistoryError(1L, 1, 20, null, "id,desc", HttpStatus.BAD_REQUEST);

        verify(applicationRepository, never()).findCandidateHistory(
                any(), any(), anyBoolean(), any());
    }

    @Test
    void acceptsMaximumPageSizeAndAscendingCreationSort() {
        User user = mock(User.class);
        Candidate candidate = mock(Candidate.class);
        when(user.getStatus()).thenReturn("ACTIVE");
        when(user.getRole()).thenReturn("CANDIDATE");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(candidateRepository.findByUserId(1L)).thenReturn(Optional.of(candidate));
        when(applicationRepository.findCandidateHistory(1L, null, true, PageRequest.of(0, 50)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));

        var result = service.findCandidateHistory(1L, 1, 50, null, "created_at,asc");

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.hasNext()).isFalse();
        verify(applicationRepository).findCandidateHistory(
                1L, null, true, PageRequest.of(0, 50));
    }

    private void assertHistoryError(
            Long userId,
            int page,
            int limit,
            String status,
            String sort,
            HttpStatus expectedStatus) {
        assertThatThrownBy(() -> service.findCandidateHistory(userId, page, limit, status, sort))
                .isInstanceOf(ApplicationException.class)
                .extracting(exception -> ((ApplicationException) exception).getStatus())
                .isEqualTo(expectedStatus);
    }
}
