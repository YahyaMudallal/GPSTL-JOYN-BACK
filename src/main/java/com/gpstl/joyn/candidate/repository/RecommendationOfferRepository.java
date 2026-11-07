package com.gpstl.joyn.candidate.repository;

import com.gpstl.joyn.joboffer.model.JobOffer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RecommendationOfferRepository extends JpaRepository<JobOffer, Long> {

    @Query(value = """
            SELECT offer.id
            FROM job_offers offer
            JOIN contract_types contract_type ON contract_type.id = offer.contract_type_id
            WHERE offer.status = 'PUBLISHED'
              AND offer.deleted_at IS NULL
              AND (offer.expires_at IS NULL OR offer.expires_at > CURRENT_TIMESTAMP)
              AND (:contractType IS NULL OR LOWER(contract_type.code) = LOWER(:contractType))
              AND (:rhythmType IS NULL OR LOWER(offer.rhythm_type) = LOWER(:rhythmType))
              AND (:remoteMode IS NULL OR offer.remote_mode = :remoteMode)
              AND (:location IS NULL OR LOWER(COALESCE(offer.location, ''))
                   LIKE CONCAT('%', LOWER(:location), '%'))
              AND NOT EXISTS (
                  SELECT 1 FROM job_swipes swipe
                  WHERE swipe.candidate_id = :candidateId AND swipe.job_offer_id = offer.id
              )
              AND NOT EXISTS (
                  SELECT 1 FROM applications application
                  WHERE application.candidate_id = :candidateId AND application.job_offer_id = offer.id
              )
            ORDER BY offer.published_at DESC NULLS LAST, offer.id DESC
            """, nativeQuery = true)
    List<Long> findEligibleOfferIds(
            @Param("candidateId") Long candidateId,
            @Param("contractType") String contractType,
            @Param("rhythmType") String rhythmType,
            @Param("remoteMode") String remoteMode,
            @Param("location") String location);

    @EntityGraph(attributePaths = {"skills", "contractType"})
    List<JobOffer> findDistinctByIdIn(List<Long> ids);
}
