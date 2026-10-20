ALTER TABLE job_offers ADD COLUMN deleted_at TIMESTAMPTZ;

-- Future recommendation/deck queries must also require status = 'PUBLISHED'.
CREATE INDEX idx_job_offers_published ON job_offers (id)
    WHERE deleted_at IS NULL AND status = 'PUBLISHED';

CREATE TABLE job_offer_audit (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    job_offer_id BIGINT NOT NULL REFERENCES job_offers(id) ON DELETE RESTRICT,
    action TEXT NOT NULL CHECK (action IN ('UPDATE', 'CLOSE')),
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
