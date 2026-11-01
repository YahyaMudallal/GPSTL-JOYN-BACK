package com.gpstl.joyn.application.repository;

import com.gpstl.joyn.application.model.Application;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    boolean existsByCandidate_IdAndJobOffer_Id(Long candidateId, Long jobOfferId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select application from Application application where application.id = :id")
    Optional<Application> findByIdForUpdate(@Param("id") Long id);

    /**
     * Loads a candidate's history with its offer, company, and optional conversation in one query.
     * The user ID comes from the authenticated JWT subject and is never supplied by the caller.
     */
    @Query(value = """
            SELECT a.id AS "id",
                   a.job_offer_id AS "jobOfferId",
                   jo.title AS "jobTitle",
                   company.id AS "companyId",
                   company.name AS "companyName",
                   application_status.code AS "status",
                   a.viewed_at AS "viewedAt",
                   a.cover_message AS "coverMessage",
                   a.resume_url AS "resumeUrl",
                   a.created_at AS "createdAt",
                   a.updated_at AS "updatedAt",
                   conversation.id AS "conversationId"
            FROM applications a
            JOIN candidate_profiles candidate ON candidate.id = a.candidate_id
            JOIN application_statuses application_status ON application_status.id = a.status_id
            JOIN job_offers jo ON jo.id = a.job_offer_id
            JOIN companies company ON company.id = jo.company_id
            LEFT JOIN conversations conversation ON conversation.application_id = a.id
            WHERE candidate.user_id = :userId
              AND (:status IS NULL OR application_status.code = :status)
            ORDER BY CASE WHEN :ascending THEN a.created_at END ASC,
                     CASE WHEN :ascending THEN a.id END ASC,
                     CASE WHEN NOT :ascending THEN a.created_at END DESC,
                     CASE WHEN NOT :ascending THEN a.id END DESC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM applications a
                    JOIN candidate_profiles candidate ON candidate.id = a.candidate_id
                    JOIN application_statuses application_status ON application_status.id = a.status_id
                    WHERE candidate.user_id = :userId
                      AND (:status IS NULL OR application_status.code = :status)
                    """,
            nativeQuery = true)
    Page<CandidateApplicationHistoryProjection> findCandidateHistory(
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("ascending") boolean ascending,
            Pageable pageable);
}
