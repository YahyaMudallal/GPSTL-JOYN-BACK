package com.gpstl.joyn.recruiter.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import com.gpstl.joyn.recruiter.model.Recruiter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    // Requête paginée avec recherche textuelle et filtres pour le back-office admin
    @Query("""
        SELECT r FROM Recruiter r
        JOIN r.user u
        LEFT JOIN r.company c
        WHERE (:search IS NULL OR :search = '' 
               OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(r.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(r.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:userStatus IS NULL OR :userStatus = '' OR u.status = :userStatus)
          AND (:companyId IS NULL OR c.id = :companyId)
    """)
    Page<Recruiter> findAdminRecruiters(
            @Param("search") String search,
            @Param("userStatus") String userStatus,
            @Param("companyId") Long companyId,
            Pageable pageable
    );
}