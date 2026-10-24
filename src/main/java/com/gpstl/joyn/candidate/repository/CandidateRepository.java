
package com.gpstl.joyn.candidate.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.gpstl.joyn.candidate.model.Candidate;

import java.util.Optional;

@Repository
public interface CandidateRepository
        extends JpaRepository<Candidate, Long> {

    Optional<Candidate> findByUserId(Long userId);

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

    /** Variante administrateur : le profil est désigné par son id (candidate_profiles.id), même verrou. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Candidate c where c.id = :id")
    Optional<Candidate> findByIdForUpdate(@Param("id") Long id);

    /** Lecture administrateur : même chargement groupé que findProfileByUserId. */
    @EntityGraph(attributePaths = {"user", "skills", "skills.skill"})
    Optional<Candidate> findProfileById(Long id);

    boolean existsByUserEmailIgnoreCase(String email);
}
