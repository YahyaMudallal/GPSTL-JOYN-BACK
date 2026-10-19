package com.gpstl.joyn.joboffer.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

import com.gpstl.joyn.joboffer.models.JobOffer;

public interface JobOfferRepository extends JpaRepository<JobOffer, Long> {
    /** Verrouille une offre non supprimée ; le statut CLOSED seul ne vaut pas suppression. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select offer from JobOffer offer where offer.id = :id and offer.deletedAt is null")
    Optional<JobOffer> findActiveByIdForUpdate(@Param("id") Long id);
}
