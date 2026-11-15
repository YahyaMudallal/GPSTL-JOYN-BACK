package com.gpstl.joyn.notification.service;

import com.gpstl.joyn.application.event.ApplicationCreated;
import com.gpstl.joyn.application.event.ApplicationStatusChanged;
import com.gpstl.joyn.application.model.Application;
import com.gpstl.joyn.application.repository.ApplicationRepository;
import com.gpstl.joyn.candidate.model.Candidate;
import com.gpstl.joyn.candidate.repository.CandidateRepository;
import com.gpstl.joyn.common.model.User;
import com.gpstl.joyn.common.repository.UserRepository;
import com.gpstl.joyn.joboffer.model.JobOffer;
import com.gpstl.joyn.joboffer.repository.JobOfferRepository;
import com.gpstl.joyn.messaging.dto.MessageItem;
import com.gpstl.joyn.messaging.event.MessagingEvent;
import com.gpstl.joyn.notification.model.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Écouteur d'événements du domaine déclenchant des notifications utilisateurs :
 *  - Candidat : changement de statut d'une candidature, réception d'un message ;
 *  - Recruteur : nouvelle candidature reçue, réception d'un message.
 */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationService notificationService;
    private final ApplicationRepository applicationRepository;
    private final CandidateRepository candidateRepository;
    private final JobOfferRepository jobOfferRepository;
    private final UserRepository userRepository;

    public NotificationEventListener(
            NotificationService notificationService,
            ApplicationRepository applicationRepository,
            CandidateRepository candidateRepository,
            JobOfferRepository jobOfferRepository,
            UserRepository userRepository) {
        this.notificationService = notificationService;
        this.applicationRepository = applicationRepository;
        this.candidateRepository = candidateRepository;
        this.jobOfferRepository = jobOfferRepository;
        this.userRepository = userRepository;
    }

    /**
     * Notifie le candidat lorsqu'un recruteur modifie le statut de sa candidature.
     */
    @EventListener
    @Transactional
    public void onApplicationStatusChanged(ApplicationStatusChanged event) {
        if (event == null || event.applicationId() == null) {
            return;
        }

        try {
            Optional<Application> optionalApplication = applicationRepository.findById(event.applicationId());
            if (optionalApplication.isEmpty()) {
                log.warn("Candidature {} introuvable pour la notification de changement de statut", event.applicationId());
                return;
            }

            Application application = optionalApplication.get();
            Candidate candidate = application.getCandidate();
            if (candidate == null || candidate.getUser() == null) {
                return;
            }

            User candidateUser = candidate.getUser();
            String jobTitle = application.getJobOffer() != null ? application.getJobOffer().getTitle() : "Offre";
            String companyName = application.getJobOffer() != null && application.getJobOffer().getCompany() != null
                    ? application.getJobOffer().getCompany().getName()
                    : "l'entreprise";

            String message = formatStatusChangeMessage(event.statusCode(), jobTitle, companyName);
            String title = "Mise à jour de votre candidature";
            String link = "/applications/" + application.getId();

            notificationService.createNotification(
                    candidateUser,
                    NotificationType.APPLICATION_UPDATE,
                    title,
                    message,
                    link);

        } catch (Exception ex) {
            log.error("Erreur lors de la notification de changement de statut pour la candidature {}",
                    event.applicationId(), ex);
        }
    }

    /**
     * Notifie le recruteur lorsqu'un candidat postule à son offre d'emploi.
     */
    @EventListener
    @Transactional
    public void onApplicationCreated(ApplicationCreated event) {
        if (event == null || event.applicationId() == null) {
            return;
        }

        try {
            User recruiterUser = null;
            if (event.recruiterUserId() != null) {
                recruiterUser = userRepository.findById(event.recruiterUserId()).orElse(null);
            }

            String jobTitle = "votre offre";
            if (event.jobOfferId() != null) {
                Optional<JobOffer> offerOpt = jobOfferRepository.findById(event.jobOfferId());
                if (offerOpt.isPresent()) {
                    JobOffer offer = offerOpt.get();
                    jobTitle = offer.getTitle();
                    if (recruiterUser == null && offer.getRecruiter() != null && offer.getRecruiter().getUser() != null) {
                        recruiterUser = offer.getRecruiter().getUser();
                    }
                }
            }

            if (recruiterUser == null) {
                log.debug("Aucun recruteur identifié pour l'offre {} lors de la candidature {}",
                        event.jobOfferId(), event.applicationId());
                return;
            }

            String candidateName = "Un candidat";
            if (event.candidateUserId() != null) {
                Optional<Candidate> candOpt = candidateRepository.findByUserId(event.candidateUserId());
                if (candOpt.isPresent()) {
                    Candidate c = candOpt.get();
                    candidateName = formatCandidateName(c);
                }
            }

            notificationService.notifyRecruiterApplication(
                    recruiterUser,
                    candidateName,
                    jobTitle,
                    event.jobOfferId());

        } catch (Exception ex) {
            log.error("Erreur lors de la notification du recruteur pour la candidature {}",
                    event.applicationId(), ex);
        }
    }

    /**
     * Notifie le destinataire (candidat ou recruteur) lors de la réception d'un nouveau message.
     */
    @EventListener
    @Transactional
    public void onMessagingEvent(MessagingEvent event) {
        if (event == null || !"message".equals(event.type()) || event.message() == null) {
            return;
        }

        try {
            MessageItem message = event.message();
            Long senderUserId = message.senderUserId();
            String senderName = message.senderName() != null ? message.senderName() : "Un utilisateur";

            if (event.recipients() == null) {
                return;
            }

            for (Long recipientUserId : event.recipients()) {
                // Ne pas notifier l'expéditeur du message lui-même
                if (recipientUserId == null || recipientUserId.equals(senderUserId)) {
                    continue;
                }

                Optional<User> recipientOpt = userRepository.findById(recipientUserId);
                if (recipientOpt.isPresent()) {
                    notificationService.notifyNewMessage(
                            recipientOpt.get(),
                            senderName,
                            message.text(),
                            message.conversationId());
                }
            }
        } catch (Exception ex) {
            log.error("Erreur lors de la notification pour le message de la conversation {}",
                    event.conversationId(), ex);
        }
    }

    private static String formatStatusChangeMessage(String statusCode, String jobTitle, String companyName) {
        if (statusCode == null) {
            return "Votre candidature pour « " + jobTitle + " » chez " + companyName + " a été mise à jour.";
        }
        return switch (statusCode) {
            case "VIEWED" -> "Votre candidature pour « " + jobTitle + " » chez " + companyName + " a été consultée par le recruteur.";
            case "IN_REVIEW" -> "Votre candidature pour « " + jobTitle + " » chez " + companyName + " est en cours d'examen.";
            case "INTERVIEW" -> "Bonne nouvelle ! Votre candidature pour « " + jobTitle + " » chez " + companyName + " a été retenue pour un entretien.";
            case "ACCEPTED" -> "Félicitations ! Votre candidature pour « " + jobTitle + " » chez " + companyName + " a été acceptée.";
            case "REJECTED" -> "Votre candidature pour « " + jobTitle + " » chez " + companyName + " n'a pas été retenue.";
            case "WITHDRAWN" -> "Votre candidature pour « " + jobTitle + " » chez " + companyName + " a été retirée.";
            default -> "Votre candidature pour « " + jobTitle + " » chez " + companyName + " est passée au statut : " + statusCode + ".";
        };
    }

    private static String formatCandidateName(Candidate c) {
        String first = c.getFirstName() != null ? c.getFirstName().trim() : "";
        String last = c.getLastName() != null ? c.getLastName().trim() : "";
        if (last.isEmpty()) {
            return first.isEmpty() ? "Un candidat" : first;
        }
        if (c.isShowLastName()) {
            return (first + " " + last).trim();
        }
        return (first + " " + last.charAt(0) + ".").trim();
    }
}
