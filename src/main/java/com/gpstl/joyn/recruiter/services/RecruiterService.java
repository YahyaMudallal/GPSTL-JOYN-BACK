package com.gpstl.joyn.recruiter.services;

import com.gpstl.joyn.common.models.User;
import com.gpstl.joyn.common.repositories.UserRepository;
import com.gpstl.joyn.recruiter.dto.RecruiterRegisterRequest;
import com.gpstl.joyn.recruiter.dto.RecruiterRegisterResponse;
import com.gpstl.joyn.recruiter.models.Company;
import com.gpstl.joyn.recruiter.models.RecruiterProfile;
import com.gpstl.joyn.recruiter.repositories.CompanyRepository;
import com.gpstl.joyn.recruiter.repositories.RecruiterProfileRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecruiterService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final RecruiterProfileRepository recruiterProfileRepository;
    private final PasswordEncoder passwordEncoder;

    // Injection des 3 repositories nécessaires via le constructeur
    public RecruiterService(
            UserRepository userRepository,
            CompanyRepository companyRepository,
            RecruiterProfileRepository recruiterProfileRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.recruiterProfileRepository = recruiterProfileRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Inscrit un nouveau recruteur en orchestrant les 3 tables :
     * 1. users
     * 2. companies
     * 3. recruiter_profiles
     */
    @Transactional
    public RecruiterRegisterResponse register(RecruiterRegisterRequest request) {
        // Vérification de l'unicité de l'email
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new IllegalArgumentException("Cet email est déjà associé à un compte.");
        }

        // Création et sauvegarde du compte générique User
        // Le mot de passe est haché avec BCrypt : on ne stocke jamais le mot de passe en clair (U16)
        User user = new User(request.email(), passwordEncoder.encode(request.password()), "RECRUITER");
        User savedUser = userRepository.save(user);

        // Récupération ou création de l'entreprise
        Company company = companyRepository.findByNameIgnoreCase(request.companyName())
                .orElseGet(() -> companyRepository.save(new Company(request.companyName())));

        // Création et sauvegarde du profil recruteur relié à l'User et à l'Entreprise
        RecruiterProfile profile = new RecruiterProfile(
                savedUser,
                company,
                request.firstName(),
                request.lastName()
        );
        RecruiterProfile savedProfile = recruiterProfileRepository.save(profile);

        // Retour du DTO formaté pour le contrôleur et Flutter
        return new RecruiterRegisterResponse(
                savedProfile.getId(),
                savedUser.getId(),
                savedUser.getEmail(),
                savedProfile.getFirstName(),
                savedProfile.getLastName(),
                company.getName()
        );
    }
}