
package com.gpstl.joyn.candidate.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.gpstl.joyn.candidate.model.Candidate;

import java.util.Optional;

@Repository
public interface CandidateRepository
        // JpaSpecificationExecutor : permet les recherches avec filtres dynamiques (liste admin, A80)
        extends JpaRepository<Candidate, Long>, JpaSpecificationExecutor<Candidate> {

    Optional<Candidate> findByUserId(Long userId);

    /** Liste admin : le compte est chargé dans la même requête, sinon chaque ligne coûterait une requête de plus. */
    @Override
    @EntityGraph(attributePaths = "user")
    Page<Candidate> findAll(Specification<Candidate> spec, Pageable pageable);

    /**
     * Lecture du profil (GET) : le compte et les compétences avec leur nom sont chargés dans la
     * même requête, sinon chaque nom de compétence coûterait une requête supplémentaire.
     * Une seule collection est jointe pour éviter le produit cartésien entre sections.
     */
    @EntityGraph(attributePaths = {"user", "skills", "skills.skill"})
    Optional<Candidate> findProfileByUserId(Long userId);

    /**
     * Charge le profil avec SELECT ... FOR UPDATE : deux mises à jour simultanées du même
     * profil s'exécutent l'une après l'autre au lieu de s'écraser. Le verrou est libéré à la
     * fin de la transaction : à appeler uniquement depuis une méthode @Transactional.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Candidate c where c.user.id = :userId")
    Optional<Candidate> findByUserIdForUpdate(@Param("userId") Long userId);

    /** Lecture par un recruteur ou un administrateur (profil désigné par son id) : même chargement groupé. */
    @EntityGraph(attributePaths = {"user", "skills", "skills.skill"})
    Optional<Candidate> findProfileById(Long id);

    boolean existsByUserEmailIgnoreCase(String email);
}
