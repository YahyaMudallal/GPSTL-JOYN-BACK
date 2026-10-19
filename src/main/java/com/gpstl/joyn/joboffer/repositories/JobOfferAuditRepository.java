package com.gpstl.joyn.joboffer.repositories;

import com.gpstl.joyn.joboffer.models.JobOfferAudit;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persiste le journal d’audit avec les méthodes fournies par Spring Data JPA. */
public interface JobOfferAuditRepository extends JpaRepository<JobOfferAudit, Long> {}
