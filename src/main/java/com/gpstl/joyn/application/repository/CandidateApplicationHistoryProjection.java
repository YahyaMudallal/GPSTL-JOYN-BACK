package com.gpstl.joyn.application.repository;

import java.time.Instant;

/**
 * Flat projection used to fetch application history and its related records in one query.
 */
public interface CandidateApplicationHistoryProjection {

    Long getId();

    Long getJobOfferId();

    String getJobTitle();

    Long getCompanyId();

    String getCompanyName();

    String getStatus();

    Instant getViewedAt();

    String getCoverMessage();

    String getResumeUrl();

    Instant getCreatedAt();

    Instant getUpdatedAt();

    Long getConversationId();
}
