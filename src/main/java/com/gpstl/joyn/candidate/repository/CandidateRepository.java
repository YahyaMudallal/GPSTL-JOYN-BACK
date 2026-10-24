
package com.gpstl.joyn.candidate.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.gpstl.joyn.candidate.model.Candidate;

import java.util.Optional;

@Repository
public interface CandidateRepository
        extends JpaRepository<Candidate, Long> {

    Optional<Candidate> findByUserId(Long userId);

    boolean existsByUserEmailIgnoreCase(String email);
}
