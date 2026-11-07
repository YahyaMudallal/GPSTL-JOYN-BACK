package com.gpstl.joyn.joboffer.repository;

import com.gpstl.joyn.application.model.Application;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecruiterJobApplicantRepository extends JpaRepository<Application, Long> {

    @Query(value = """
            SELECT application.id AS "applicationId",
                   candidate.id AS "candidateId",
                   application_status.code AS "applicationStatus",
                   application.created_at AS "appliedAt",
                   recommendation.match_score AS "matchScore",
                   recommendation.skill_score AS "skillScore",
                   recommendation.experience_score AS "experienceScore",
                   recommendation.education_score AS "educationScore",
                   recommendation.location_score AS "locationScore",
                   recommendation.availability_score AS "availabilityScore",
                   recommendation.distance_km AS "distanceKm"
            FROM applications application
            JOIN candidate_profiles candidate ON candidate.id = application.candidate_id
            JOIN users candidate_user ON candidate_user.id = candidate.user_id
            JOIN application_statuses application_status ON application_status.id = application.status_id
            LEFT JOIN candidate_recommendation_scores recommendation
                   ON recommendation.candidate_id = candidate.id
                  AND recommendation.job_offer_id = application.job_offer_id
            WHERE application.job_offer_id = :jobId
              AND candidate_user.status = 'ACTIVE'
              AND candidate.is_visible_to_recruiters = TRUE
            ORDER BY recommendation.match_score DESC NULLS LAST,
                     application.created_at DESC,
                     application.id DESC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM applications application
                    JOIN candidate_profiles candidate ON candidate.id = application.candidate_id
                    JOIN users candidate_user ON candidate_user.id = candidate.user_id
                    LEFT JOIN candidate_recommendation_scores recommendation
                           ON recommendation.candidate_id = candidate.id
                          AND recommendation.job_offer_id = application.job_offer_id
                    WHERE application.job_offer_id = :jobId
                      AND candidate_user.status = 'ACTIVE'
                      AND candidate.is_visible_to_recruiters = TRUE
                    """,
            nativeQuery = true)
    Page<RecruiterJobApplicantProjection> findApplicants(
            @Param("jobId") Long jobId,
            Pageable pageable);
}
