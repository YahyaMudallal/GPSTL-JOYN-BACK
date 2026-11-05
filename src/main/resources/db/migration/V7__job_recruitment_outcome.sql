ALTER TABLE job_offers
ADD COLUMN recruitment_outcome VARCHAR(20),
ADD COLUMN hired_application_id BIGINT
    REFERENCES applications(id);

ALTER TABLE job_offers
ADD CONSTRAINT ck_job_recruitment_outcome
CHECK (
    recruitment_outcome IS NULL
    OR recruitment_outcome IN ('FILLED', 'NOT_FILLED', 'ONGOING')
);

ALTER TABLE job_offers
ADD CONSTRAINT ck_job_hired_application
CHECK (
    (recruitment_outcome = 'FILLED' AND hired_application_id IS NOT NULL)
    OR (recruitment_outcome IS DISTINCT FROM 'FILLED' AND hired_application_id IS NULL)
);