
package com.gpstl.joyn.joboffer.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

import com.gpstl.joyn.joboffer.models.JobOffer;
import com.gpstl.joyn.joboffer.models.OfferStatus;

public interface JobOfferRepository extends JpaRepository<JobOffer, Long> {

    // Existing method from your teammates - unchanged
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select offer from JobOffer offer where offer.id = :id and offer.deletedAt is null")
    Optional<JobOffer> findActiveByIdForUpdate(@Param("id") Long id);

    // R65 - Retrieve active job offers for a company
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
