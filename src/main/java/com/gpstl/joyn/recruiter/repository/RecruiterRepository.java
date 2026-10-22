package com.gpstl.joyn.recruiter.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.gpstl.joyn.recruiter.model.Recruiter;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecruiterRepository extends JpaRepository<Recruiter, Long> {

    // Retrouve le profil d'un recruteur à partir de son compte User générique
    Optional<Recruiter> findByUserId(Long userId);

    // Liste tous les recruteurs rattachés à une entreprise donnée
    List<Recruiter> findByCompanyId(Long companyId);
}