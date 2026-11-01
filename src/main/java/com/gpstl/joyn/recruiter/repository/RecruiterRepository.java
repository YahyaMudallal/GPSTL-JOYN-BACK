package com.gpstl.joyn.recruiter.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.gpstl.joyn.recruiter.model.Recruiter;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecruiterRepository extends JpaRepository<Recruiter, Long> {

    // Retrouve le profil d'un recruteur à partir de son compte User générique
    Optional<Recruiter> findByUserId(Long userId);

    // Verrouille le profil jusqu'à la fin de la transaction, pour sa mise à jour
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Recruiter r where r.user.id = :userId")
    Optional<Recruiter> findByUserIdForUpdate(@Param("userId") Long userId);

    // Liste tous les recruteurs rattachés à une entreprise donnée
    List<Recruiter> findByCompanyId(Long companyId);
}