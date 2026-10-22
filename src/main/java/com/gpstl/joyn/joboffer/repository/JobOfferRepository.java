package com.gpstl.joyn.joboffer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gpstl.joyn.joboffer.model.JobOffer;
import com.gpstl.joyn.joboffer.model.OfferStatus;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface JobOfferRepository extends JpaRepository<JobOffer, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select offer from JobOffer offer where offer.id = :id and offer.deletedAt is null")
    Optional<JobOffer> findActiveByIdForUpdate(@Param("id") Long id);

    @Query("""
    SELECT j FROM JobOffer j
    WHERE j.company.id = :companyId
      AND j.status = :status
      AND j.deletedAt IS NULL
      AND (j.expiresAt IS NULL OR j.expiresAt > CURRENT_TIMESTAMP)
    """)
List<JobOffer> findPublicOffersByCompanyId(
        @Param("companyId") Long companyId,
        @Param("status") OfferStatus status
);
}
