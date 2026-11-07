package com.gpstl.joyn.candidate.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gpstl.joyn.candidate.model.CandidateRecommendationScore;

import java.util.Collection;
import java.util.List;

public interface CandidateRecommendationScoreRepository
    extends JpaRepository<CandidateRecommendationScore, Long> {

    List<CandidateRecommendationScore> findAllByCandidate_IdAndJobOffer_IdIn(
        Long candidateId, 
        Collection<Long> jobOfferIds);
}
