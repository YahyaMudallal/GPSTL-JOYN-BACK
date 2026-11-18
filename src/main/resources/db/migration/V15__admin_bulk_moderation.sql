ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ;

CREATE TABLE admin_moderation_step_up (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    last_totp_step BIGINT NOT NULL DEFAULT -1,
    window_started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    attempts INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE admin_removal_audit (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_user_id BIGINT NOT NULL REFERENCES users(id),
    target_type TEXT NOT NULL CHECK (target_type IN ('users', 'companies')),
    target_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE company_verification_reviews (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    actor_user_id BIGINT NOT NULL REFERENCES users(id),
    method TEXT NOT NULL CHECK (method IN ('MANUAL','DOCUMENT','REQUEST','REFUSED','REGISTRY')),
    note TEXT NOT NULL DEFAULT '',
    evidence_pdf BYTEA CHECK (octet_length(evidence_pdf) <= 5242880),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_company_verification_reviews_company ON company_verification_reviews(company_id, id DESC);
