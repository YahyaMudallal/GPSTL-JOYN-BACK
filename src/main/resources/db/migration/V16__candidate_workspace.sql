-- Durable candidate-only tools. All rows are linked to the authenticated users table.
CREATE TABLE candidate_offer_bookmarks (
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  job_offer_id BIGINT NOT NULL REFERENCES job_offers(id) ON DELETE CASCADE,
  state TEXT NOT NULL CHECK (state IN ('SAVED','INTERESTED','HIDDEN')),
  collection_name VARCHAR(60) NOT NULL DEFAULT 'Plus tard',
  note VARCHAR(3000),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY(user_id, job_offer_id)
);
CREATE INDEX idx_candidate_bookmarks_state ON candidate_offer_bookmarks(user_id,state);
CREATE TRIGGER trg_candidate_offer_bookmarks_updated BEFORE UPDATE ON candidate_offer_bookmarks FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE candidate_workspace_preferences (
  user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  weekly_goal INTEGER NOT NULL DEFAULT 5 CHECK (weekly_goal BETWEEN 1 AND 100),
  alerts_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  saved_filters JSONB NOT NULL DEFAULT '{}'::jsonb,
  hidden_widgets JSONB NOT NULL DEFAULT '[]'::jsonb,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TRIGGER trg_candidate_workspace_preferences_updated BEFORE UPDATE ON candidate_workspace_preferences FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE candidate_saved_searches (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name VARCHAR(100) NOT NULL,
  filters JSONB NOT NULL DEFAULT '{}'::jsonb,
  alerts_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_candidate_saved_searches_user ON candidate_saved_searches(user_id);

CREATE TABLE candidate_portfolio_projects (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title VARCHAR(150) NOT NULL,
  description VARCHAR(3000) NOT NULL DEFAULT '',
  project_url VARCHAR(1200),
  skills VARCHAR(1200) NOT NULL DEFAULT '',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TRIGGER trg_candidate_portfolio_projects_updated BEFORE UPDATE ON candidate_portfolio_projects FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE INDEX idx_candidate_portfolio_projects_user ON candidate_portfolio_projects(user_id);

CREATE TABLE candidate_application_notes (
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  application_id BIGINT NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
  note VARCHAR(3000) NOT NULL DEFAULT '',
  remind_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY(user_id,application_id)
);
CREATE TRIGGER trg_candidate_application_notes_updated BEFORE UPDATE ON candidate_application_notes FOR EACH ROW EXECUTE FUNCTION set_updated_at();
ALTER TABLE candidate_saved_searches ADD COLUMN last_checked_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- Application change log records actual events from this migration forward.
CREATE TABLE candidate_application_status_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id BIGINT NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    status_code VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_candidate_status_events_application ON candidate_application_status_events(application_id,created_at);
CREATE OR REPLACE FUNCTION log_candidate_application_status() RETURNS TRIGGER AS $$
BEGIN
  IF TG_OP = 'INSERT' OR NEW.status_id IS DISTINCT FROM OLD.status_id THEN
    INSERT INTO candidate_application_status_events(application_id,status_code)
      SELECT NEW.id,code FROM application_statuses WHERE id=NEW.status_id;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_candidate_application_status_log
  AFTER INSERT OR UPDATE OF status_id ON applications
  FOR EACH ROW EXECUTE FUNCTION log_candidate_application_status();

CREATE TABLE candidate_application_drafts (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_offer_id BIGINT NOT NULL REFERENCES job_offers(id) ON DELETE CASCADE,
    cover_message VARCHAR(10000) NOT NULL DEFAULT '',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY(user_id,job_offer_id)
);
CREATE TRIGGER trg_candidate_application_drafts_updated BEFORE UPDATE ON candidate_application_drafts
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE candidate_cv_versions (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  label VARCHAR(100) NOT NULL,
  filename VARCHAR(180) NOT NULL,
  storage_key TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_candidate_cv_versions_user ON candidate_cv_versions(user_id,created_at DESC);
