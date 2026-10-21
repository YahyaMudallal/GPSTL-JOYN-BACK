package com.gpstl.joyn.joboffer.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gpstl.joyn.joboffer.model.JobOfferAudit;

/** Persiste le journal d’audit avec les méthodes fournies par Spring Data JPA. */
public interface JobOfferAuditRepository extends JpaRepository<JobOfferAudit, Long> {}
