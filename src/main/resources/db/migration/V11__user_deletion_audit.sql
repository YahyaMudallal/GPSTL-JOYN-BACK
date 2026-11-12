CREATE TABLE user_deletion_audit (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    deleted_user_id BIGINT NOT NULL,
    deleted_by BIGINT NOT NULL,
    ip_address VARCHAR(45),
    reason TEXT,
    deleted_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
