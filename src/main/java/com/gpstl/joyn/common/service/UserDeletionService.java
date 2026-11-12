
package com.gpstl.joyn.common.service;

import com.gpstl.joyn.candidate.repository.CandidateRepository;
import com.gpstl.joyn.candidate.service.CandidateResumeStorage;
import com.gpstl.joyn.common.model.User;
import com.gpstl.joyn.common.repository.UserRepository;
import com.gpstl.joyn.recruiter.repository.RecruiterRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class UserDeletionService {

    private static final Logger log =
            LoggerFactory.getLogger(UserDeletionService.class);

    private final UserRepository userRepository;
    private final CandidateRepository candidateRepository;
    private final RecruiterRepository recruiterRepository;
    private final JdbcTemplate jdbcTemplate;
    private final CandidateResumeStorage candidateResumeStorage;

    public UserDeletionService(
            UserRepository userRepository,
            CandidateRepository candidateRepository,
            RecruiterRepository recruiterRepository,
            JdbcTemplate jdbcTemplate,
            CandidateResumeStorage candidateResumeStorage) {

        this.userRepository = userRepository;
        this.candidateRepository = candidateRepository;
        this.recruiterRepository = recruiterRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.candidateResumeStorage = candidateResumeStorage;
    }

    @Transactional
    public void deleteUser(
            Long userId,
            Jwt jwt,
            String reason,
            String ipAddress) {

        Long currentUserId = Long.valueOf(jwt.getSubject());
        boolean isAdmin = "ADMIN".equals(jwt.getClaimAsString("role"));

        // Only the owner or an administrator can delete the account.
        if (!isAdmin && !currentUserId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        // Prevent administrators from deleting their own accounts.
        if (isAdmin && currentUserId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        // Find the user and verify that the account is not deleted.
        User user = userRepository.findById(userId)
                .filter(u -> !"DELETED".equals(u.getStatus()))
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND));

        // Collect CV keys before removing database references.
        Set<String> resumeKeysToDelete = new LinkedHashSet<>();

        // Anonymize candidate information.
        candidateRepository.findByUserId(userId).ifPresent(candidate -> {

            // Collect the candidate's main CV.
            if (candidate.getResumeUrl() != null) {
                resumeKeysToDelete.add(candidate.getResumeUrl());
            }

            // Collect CVs attached to previous applications.
            resumeKeysToDelete.addAll(
                    jdbcTemplate.queryForList(
                            """
                            SELECT resume_url
                            FROM applications
                            WHERE candidate_id = ?
                              AND resume_url IS NOT NULL
                            """,
                            String.class,
                            candidate.getId()
                    )
            );

            // Remove personal information from applications.
            jdbcTemplate.update(
                    """
                    UPDATE applications
                    SET cover_message = NULL,
                        resume_url = NULL
                    WHERE candidate_id = ?
                    """,
                    candidate.getId()
            );

            candidate.anonymize();
            candidateRepository.save(candidate);
        });

        // Anonymize recruiter information.
        recruiterRepository.findByUserId(userId).ifPresent(recruiter -> {

            long activeRecruiters = recruiterRepository
                    .findByCompanyId(recruiter.getCompany().getId())
                    .stream()
                    .filter(r -> "ACTIVE".equals(r.getUser().getStatus()))
                    .count();

            // Prevent deleting the company's last active recruiter.
            if (activeRecruiters <= 1) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Transfer company responsibility before deleting this account"
                );
            }

            recruiter.anonymize();
            recruiterRepository.save(recruiter);
        });

        // Remove password-reset and email-verification tokens.
        jdbcTemplate.update(
                "DELETE FROM auth_tokens WHERE user_id = ?",
                userId
        );

        // Anonymize messages sent by the user.
        jdbcTemplate.update(
                """
                UPDATE messages
                SET content = '[Message supprimé]',
                    attachment_url = NULL
                WHERE sender_user_id = ?
                """,
                userId
        );

        // Remove the user's notifications.
        jdbcTemplate.update(
                "DELETE FROM notifications WHERE user_id = ?",
                userId
        );

        // Anonymize the user account.
        user.anonymize();
        userRepository.save(user);

        // Record the deletion in the same database transaction.
        jdbcTemplate.update(
                """
                INSERT INTO user_deletion_audit
                    (deleted_user_id, deleted_by, ip_address, reason)
                VALUES (?, ?, ?, ?)
                """,
                userId,
                currentUserId,
                ipAddress,
                reason
        );

        // Physically delete CV files after the database transaction commits.
        if (!resumeKeysToDelete.isEmpty()) {

            Set<String> keys = Set.copyOf(resumeKeysToDelete);

            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {

                        @Override
                        public void afterCommit() {
                            for (String key : keys) {

                                if (!CandidateResumeStorage.STORAGE_KEY_PATTERN
                                        .matcher(key).matches()) {
                                    log.warn(
                                            "Skipping unsupported CV storage key during account deletion"
                                    );
                                    continue;
                                }

                                try {
                                    candidateResumeStorage.delete(key);
                                } catch (RuntimeException exception) {
                                    log.error(
                                            "CV cleanup failed after deletion of account {}",
                                            userId,
                                            exception
                                    );
                                }
                            }
                        }
                    }
            );
        }
    }
}
