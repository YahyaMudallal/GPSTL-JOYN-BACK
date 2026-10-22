package com.gpstl.joyn.recruiter.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.gpstl.joyn.recruiter.model.RecruiterProfile;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecruiterProfileRepository extends JpaRepository<RecruiterProfile, Long> {

    // Retrouve le profil d'un recruteur à partir de son compte User générique
    Optional<RecruiterProfile> findByUserId(Long userId);

    // Liste tous les recruteurs rattachés à une entreprise donnée
    List<RecruiterProfile> findByCompanyId(Long companyId);
}