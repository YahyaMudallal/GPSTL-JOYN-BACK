package com.gpstl.joyn.notification.service;

import com.gpstl.joyn.application.event.ApplicationCreated;
import com.gpstl.joyn.application.event.ApplicationStatusChanged;
import com.gpstl.joyn.application.model.Application;
import com.gpstl.joyn.application.repository.ApplicationRepository;
import com.gpstl.joyn.candidate.model.Candidate;
import com.gpstl.joyn.candidate.repository.CandidateRepository;
import com.gpstl.joyn.common.model.User;
import com.gpstl.joyn.common.repository.UserRepository;
import com.gpstl.joyn.company.model.Company;
import com.gpstl.joyn.joboffer.model.JobOffer;
import com.gpstl.joyn.joboffer.repository.JobOfferRepository;
import com.gpstl.joyn.messaging.dto.MessageItem;
import com.gpstl.joyn.messaging.event.MessagingEvent;
import com.gpstl.joyn.notification.model.NotificationType;
import com.gpstl.joyn.recruiter.model.Recruiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NotificationEventListenerTest {

    private NotificationService notificationService;
    private ApplicationRepository applicationRepository;
    private CandidateRepository candidateRepository;
    private JobOfferRepository jobOfferRepository;
    private UserRepository userRepository;
    private NotificationEventListener listener;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        applicationRepository = mock(ApplicationRepository.class);
        candidateRepository = mock(CandidateRepository.class);
        jobOfferRepository = mock(JobOfferRepository.class);
        userRepository = mock(UserRepository.class);

        listener = new NotificationEventListener(
                notificationService,
                applicationRepository,
                candidateRepository,
                jobOfferRepository,
                userRepository);
    }

    @Test
    void onApplicationStatusChanged_notifiesCandidate() {
        Long appId = 42L;
        User candidateUser = mock(User.class);
        Candidate candidate = mock(Candidate.class);
        when(candidate.getUser()).thenReturn(candidateUser);

        Company company = mock(Company.class);
        when(company.getName()).thenReturn("Tech Corp");

        JobOffer job = mock(JobOffer.class);
        when(job.getTitle()).thenReturn("Ingénieur DevOps");
        when(job.getCompany()).thenReturn(company);

        Application application = mock(Application.class);
        when(application.getId()).thenReturn(appId);
        when(application.getCandidate()).thenReturn(candidate);
        when(application.getJobOffer()).thenReturn(job);

        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));

        listener.onApplicationStatusChanged(new ApplicationStatusChanged(appId, "INTERVIEW"));

        verify(notificationService).createNotification(
                eq(candidateUser),
                eq(NotificationType.APPLICATION_UPDATE),
                eq("Mise à jour de votre candidature"),
                contains("retenue pour un entretien"),
                eq("/applications/42"));
    }

    @Test
    void onApplicationCreated_notifiesRecruiter() {
        Long appId = 100L;
        Long jobId = 20L;
        Long candUserId = 1L;
        Long recUserId = 2L;

        User recruiterUser = mock(User.class);
        when(userRepository.findById(recUserId)).thenReturn(Optional.of(recruiterUser));

        Candidate candidate = mock(Candidate.class);
        when(candidate.getFirstName()).thenReturn("Lucas");
        when(candidate.getLastName()).thenReturn("Bernard");
        when(candidate.isShowLastName()).thenReturn(false);
        when(candidateRepository.findByUserId(candUserId)).thenReturn(Optional.of(candidate));

        JobOffer job = mock(JobOffer.class);
        when(job.getTitle()).thenReturn("Développeur React");
        when(jobOfferRepository.findById(jobId)).thenReturn(Optional.of(job));

        listener.onApplicationCreated(new ApplicationCreated(appId, jobId, candUserId, recUserId));

        verify(notificationService).notifyRecruiterApplication(
                eq(recruiterUser),
                eq("Lucas B."),
                eq("Développeur React"),
                eq(jobId));
    }

    @Test
    void onApplicationCreated_recruiterInJobOfferFallback() {
        Long appId = 101L;
        Long jobId = 21L;
        Long candUserId = 1L;

        User recruiterUser = mock(User.class);
        Recruiter recruiter = mock(Recruiter.class);
        when(recruiter.getUser()).thenReturn(recruiterUser);

        JobOffer job = mock(JobOffer.class);
        when(job.getTitle()).thenReturn("Architecte Cloud");
        when(job.getRecruiter()).thenReturn(recruiter);
        when(jobOfferRepository.findById(jobId)).thenReturn(Optional.of(job));

        when(candidateRepository.findByUserId(candUserId)).thenReturn(Optional.empty());

        listener.onApplicationCreated(new ApplicationCreated(appId, jobId, candUserId, null));

        verify(notificationService).notifyRecruiterApplication(
                eq(recruiterUser),
                eq("Un candidat"),
                eq("Architecte Cloud"),
                eq(jobId));
    }

    @Test
    void onMessagingEvent_notifiesRecipientsExceptSender() {
        Long senderId = 1L;
        Long recipient1Id = 2L;
        Long recipient2Id = 3L;

        MessageItem message = new MessageItem(
                10L, 5L, senderId, "CANDIDATE", "Camille", true, "Bonjour !",
                OffsetDateTime.now(), null, "client-1");

        MessagingEvent event = new MessagingEvent("message", 5L, Set.of(senderId, recipient1Id, recipient2Id), message, null);

        User user2 = mock(User.class);
        User user3 = mock(User.class);
        when(userRepository.findById(recipient1Id)).thenReturn(Optional.of(user2));
        when(userRepository.findById(recipient2Id)).thenReturn(Optional.of(user3));

        listener.onMessagingEvent(event);

        verify(notificationService).notifyNewMessage(eq(user2), eq("Camille"), eq("Bonjour !"), eq(5L));
        verify(notificationService).notifyNewMessage(eq(user3), eq("Camille"), eq("Bonjour !"), eq(5L));
        verify(notificationService, never()).notifyNewMessage(argThat(u -> u != user2 && u != user3), any(), any(), any());
    }

    @Test
    void onMessagingEvent_ignoresReadEvents() {
        MessagingEvent event = new MessagingEvent("read", 5L, Set.of(2L), null, 1L);

        listener.onMessagingEvent(event);

        verifyNoInteractions(notificationService);
    }
}
