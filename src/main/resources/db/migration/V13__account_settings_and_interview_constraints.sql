ALTER TABLE users ADD COLUMN settings JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE users ADD COLUMN tokens_valid_after TIMESTAMPTZ;
ALTER TABLE interviews ADD CONSTRAINT ck_interview_duration CHECK (duration_minutes BETWEEN 5 AND 480);
CREATE INDEX idx_interviews_schedule_status ON interviews(scheduled_at, status);
CREATE TABLE account_status_audit (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_user_id BIGINT NOT NULL REFERENCES users(id),
    target_user_id BIGINT NOT NULL REFERENCES users(id),
    status TEXT NOT NULL CHECK(status IN ('ACTIVE','SUSPENDED')),
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
