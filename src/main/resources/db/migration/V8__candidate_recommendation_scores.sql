CREATE TABLE candidate_recommendation_scores (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    candidate_id BIGINT NOT NULL
        REFERENCES candidate_profiles(id) ON DELETE CASCADE,
    job_offer_id BIGINT NOT NULL
        REFERENCES job_offers(id) ON DELETE CASCADE,
    match_score SMALLINT NOT NULL CHECK (match_score BETWEEN 0 AND 100),
    skill_score SMALLINT CHECK (skill_score BETWEEN 0 AND 100),
    experience_score SMALLINT CHECK (experience_score BETWEEN 0 AND 100),
    education_score SMALLINT CHECK (education_score BETWEEN 0 AND 100),
    location_score SMALLINT CHECK (location_score BETWEEN 0 AND 100),
    availability_score SMALLINT CHECK (availability_score BETWEEN 0 AND 100),
    calculated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_candidate_recommendation_score UNIQUE (candidate_id, job_offer_id)
);

CREATE INDEX idx_candidate_recommendation_scores_offer
    ON candidate_recommendation_scores (job_offer_id, match_score DESC);


