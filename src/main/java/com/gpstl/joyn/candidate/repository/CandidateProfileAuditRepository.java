package com.gpstl.joyn.candidate.repository;

import com.gpstl.joyn.candidate.model.CandidateProfileAudit;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persiste le journal des modifications de profil faites par un administrateur. */
public interface CandidateProfileAuditRepository extends JpaRepository<CandidateProfileAudit, Long> {}
