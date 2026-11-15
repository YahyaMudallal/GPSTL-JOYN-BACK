package com.gpstl.joyn.user.service;

import com.gpstl.joyn.candidate.model.Candidate;
import com.gpstl.joyn.candidate.repository.CandidateRepository;
import com.gpstl.joyn.common.model.User;
import com.gpstl.joyn.common.repository.UserRepository;
import com.gpstl.joyn.user.dto.ProfilePictureResponse;
import com.gpstl.joyn.user.exception.UserProfilePictureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

/**
 * Service métier pour la gestion des photos de profil utilisateur.
 * Gère l'upload, le traitement, le stockage WebP, le nettoyage et la suppression.
 */
@Service
public class UserProfilePictureService {

    private static final Logger log = LoggerFactory.getLogger(UserProfilePictureService.class);

    private final UserRepository userRepository;
    private final CandidateRepository candidateRepository;
    private final ProfilePictureImageProcessor imageProcessor;
    private final UserProfilePictureStorage storage;

    public UserProfilePictureService(
            UserRepository userRepository,
            CandidateRepository candidateRepository,
            ProfilePictureImageProcessor imageProcessor,
            UserProfilePictureStorage storage
    ) {
        this.userRepository = userRepository;
        this.candidateRepository = candidateRepository;
        this.imageProcessor = imageProcessor;
        this.storage = storage;
    }

    /**
     * Traite, redimensionne et enregistre la nouvelle photo de profil de l'utilisateur.
     * Supprime automatiquement l'ancienne image stockée pour optimiser l'espace disque.
     */
    @Transactional
    public ProfilePictureResponse updateProfilePicture(Long userId, MultipartFile file) {
        User user = findActiveUser(userId);

        // Traitement de l'image (validation, recadrage 1:1, 512x512, WebP 80%)
        byte[] webpBytes = imageProcessor.processProfilePicture(file);

        // Suppression de l'ancienne photo dans le stockage si existante
        String oldKey = user.getProfilePictureUrl();
        if (oldKey != null && !oldKey.isBlank()) {
            storage.delete(oldKey);
        }

        // Sauvegarde de la nouvelle photo dans le bucket Supabase
        String newStorageKey = storage.store(userId, webpBytes);

        // Mise à jour de l'entité User
        user.setProfilePictureUrl(newStorageKey);
        userRepository.save(user);

        // Synchronisation avec le profil candidat si applicable
        Optional<Candidate> candidate = candidateRepository.findByUserId(userId);
        candidate.ifPresent(c -> {
            c.setPhotoUrl(newStorageKey);
            candidateRepository.save(c);
        });

        String accessibleUrl = storage.createPresignedUrl(newStorageKey);
        log.info("Photo de profil mise à jour pour l'utilisateur id={}", userId);
        return ProfilePictureResponse.of(accessibleUrl);
    }

    /**
     * Supprime la photo de profil de l'utilisateur du stockage et réinitialise les colonnes en base.
     */
    @Transactional
    public void deleteProfilePicture(Long userId) {
        User user = findActiveUser(userId);

        String currentKey = user.getProfilePictureUrl();

        user.setProfilePictureUrl(null);
        userRepository.save(user);

        Optional<Candidate> candidate = candidateRepository.findByUserId(userId);
        candidate.ifPresent(c -> {
            c.setPhotoUrl(null);
            candidateRepository.save(c);
        });

        if (currentKey != null && !currentKey.isBlank()) {
            storage.delete(currentKey);
        }
        log.info("Photo de profil retirée pour l'utilisateur id={}", userId);
    }

    /**
     * Récupère l'URL d'accès à la photo de profil de l'utilisateur.
     */
    @Transactional(readOnly = true)
    public ProfilePictureResponse getProfilePicture(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserProfilePictureException(404, "Utilisateur non trouvé."));

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new UserProfilePictureException(403, "Le compte utilisateur n'est pas actif.");
        }

        String key = user.getProfilePictureUrl();
        String url = storage.createPresignedUrlIfValid(key);
        return ProfilePictureResponse.of(url);
    }

    private User findActiveUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new UserProfilePictureException(401, "Identifiant utilisateur invalide.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserProfilePictureException(404, "Utilisateur introuvable."));
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new UserProfilePictureException(403, "Compte inactif ou suspendu.");
        }
        return user;
    }
}
