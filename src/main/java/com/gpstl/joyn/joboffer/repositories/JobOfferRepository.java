package com.gpstl.joyn.joboffer.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gpstl.joyn.joboffer.models.JobOffer;

public interface JobOfferRepository extends JpaRepository<JobOffer, Long> {
}
