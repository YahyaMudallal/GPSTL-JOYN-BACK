package com.gpstl.joyn.joboffer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gpstl.joyn.joboffer.model.JobOffer;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface JobOfferRepository extends JpaRepository<JobOffer, Long> {
    /** Verrouille une offre non supprimée ; le statut CLOSED seul ne vaut pas suppression. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select offer from JobOffer offer where offer.id = :id and offer.deletedAt is null")
    Optional<JobOffer> findActiveByIdForUpdate(@Param("id") Long id);
}
