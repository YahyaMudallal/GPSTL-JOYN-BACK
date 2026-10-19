package com.gpstl.joyn.repository;

import com.gpstl.joyn.entity.RecruiterProfile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecruiterProfileRepository extends JpaRepository<RecruiterProfile, Long> {

    /** Retrouve le profil recruteur (et son entreprise) à partir de l'id du compte utilisateur. */
    @EntityGraph(attributePaths = "company")
    Optional<RecruiterProfile> findByUserId(Long userId);
}
