package com.gpstl.joyn.recruiter.service;

import com.gpstl.joyn.common.model.User;
import com.gpstl.joyn.common.repository.UserRepository;
import com.gpstl.joyn.recruiter.dto.RecruiterProfilePatch;
import com.gpstl.joyn.recruiter.dto.RecruiterProfileResponse;
import com.gpstl.joyn.recruiter.exception.RecruiterProfileException;
import com.gpstl.joyn.recruiter.model.Recruiter;
import com.gpstl.joyn.recruiter.repository.RecruiterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Profil recruteur (R78) : un recruteur actif lit son profil et modifie son propre nom.
 * Le compte est relu en base, en plus du contrôle du token par SecurityConfig :
 * un token émis avant une suspension ou un changement de rôle ne suffit pas.
 */
@Service
public class RecruiterProfileService {
    private final UserRepository users;
    private final RecruiterRepository recruiters;

    public RecruiterProfileService(UserRepository users, RecruiterRepository recruiters) {
        this.users = users;
        this.recruiters = recruiters;
    }

    @Transactional(readOnly = true)
    public RecruiterProfileResponse getProfile(long userId) {
        User user = checkRecruiterAccount(userId);
        Recruiter recruiter = recruiters.findByUserId(userId)
                .orElseThrow(RecruiterProfileService::profileNotFound);
        return toResponse(recruiter, user);
    }

    @Transactional
    public RecruiterProfileResponse updateProfile(long userId, Map<String, Object> body) {
        // Le compte d'abord : un compte non autorisé reçoit 403 même si son JSON est invalide.
        User user = checkRecruiterAccount(userId);
        RecruiterProfilePatch patch = RecruiterProfilePatch.parse(body);

        // SELECT ... FOR UPDATE : une mise à jour simultanée de l'autre champ n'est pas écrasée.
        Recruiter recruiter = recruiters.findByUserIdForUpdate(userId)
                .orElseThrow(RecruiterProfileService::profileNotFound);
        if (patch.firstName() != null) recruiter.setFirstName(patch.firstName());
        if (patch.lastName() != null) recruiter.setLastName(patch.lastName());
        recruiters.saveAndFlush(recruiter);
        return toResponse(recruiter, user);
    }

    // À appeler dans la transaction : l'entreprise est chargée à la demande.
    private static RecruiterProfileResponse toResponse(Recruiter recruiter, User user) {
        return new RecruiterProfileResponse(recruiter.getId(), user.getId(), user.getEmail(),
                recruiter.getFirstName(), recruiter.getLastName(), recruiter.getCompany().getName());
    }

    private static RecruiterProfileException profileNotFound() {
        return new RecruiterProfileException(404, "Recruiter profile not found");
    }

    /** Utilisateur absent : 401 ; compte suspendu ou rôle non autorisé : 403. */
    private User checkRecruiterAccount(long userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new RecruiterProfileException(401, "Authenticated user does not exist"));
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new RecruiterProfileException(403, "Account is suspended");
        }
        if (!"RECRUITER".equals(user.getRole())) {
            throw new RecruiterProfileException(403, "Only recruiters have a recruiter profile");
        }
        return user;
    }
}
