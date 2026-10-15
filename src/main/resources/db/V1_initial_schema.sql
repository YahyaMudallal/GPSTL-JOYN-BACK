-- ---------------------------------------------------------------------
-- Mise à jour automatique de updated_at
--    (DEFAULT NOW() ne joue qu'à l'INSERT, pas à l'UPDATE)
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- ---------------------------------------------------------------------
-- Utilisateurs
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'CANDIDATE'
        CHECK (role IN ('CANDIDATE', 'RECRUITER', 'ADMIN')),

    status TEXT NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'SUSPENDED')),      -- admin : Suspendre / Réactiver
    
    email_verified_at TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE UNIQUE INDEX uq_users_email_lower ON users (LOWER(email));

-- Jetons de vérification d'email et de mot de passe oublié
-- (on stocke le hash du jeton, jamais le jeton en clair)
CREATE TABLE auth_tokens (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL
        REFERENCES users(id) ON DELETE CASCADE,
    type TEXT NOT NULL
        CHECK (type IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET')),
    token_hash TEXT NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------------
-- Profil candidat
-- ---------------------------------------------------------------------
CREATE TABLE candidate_profiles (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE
        REFERENCES users(id) ON DELETE CASCADE,

    first_name TEXT NOT NULL,
    last_name TEXT NOT NULL,
    location TEXT,
    headline VARCHAR(255),                 -- "Développeuse Full-Stack en devenir"
    bio TEXT,

    -- Si alternance 
    rhythm_type TEXT,

    available_from DATE,           -- "Disponible dès sept. 2027"
    contract_duration_months SMALLINT,
    adress TEXT,                            -- "Paris 9e"

    github_url TEXT,
    linkedin_url TEXT,
    portfolio_url TEXT,

    -- Visibilité (story sécurité)
    is_visible_to_recruiters BOOLEAN NOT NULL DEFAULT TRUE,
    is_visible_to_others BOOLEAN NOT NULL DEFAULT TRUE,
    show_last_name BOOLEAN NOT NULL DEFAULT TRUE,
    show_contact_links BOOLEAN NOT NULL DEFAULT TRUE,

    resume_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE candidate_educations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    candidate_id BIGINT NOT NULL
        REFERENCES candidate_profiles(id) ON DELETE CASCADE,
    school_name TEXT NOT NULL,
    school_address TEXT,
    field_of_study TEXT NOT NULL,
    degree TEXT NOT NULL
        CHECK (degree IN ('BACHELORS', 'MASTERS', 'ENGINEERING', 'DOCTORATE', 'OTHER')),
    postbac_year TEXT
        CHECK (postbac_year IN ('BAC_2', 'BAC_3', 'BAC_4', 'BAC_5')),
    start_year SMALLINT,
    end_year SMALLINT,
    currently_studying BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE candidate_experiences (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    candidate_id BIGINT NOT NULL
        REFERENCES candidate_profiles(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    organization TEXT,
    description TEXT,
    start_date DATE,
    end_date DATE,
    currently_working BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------------
-- Entreprises
-- ---------------------------------------------------------------------
CREATE TABLE companies (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    website TEXT,
    logo_url TEXT,

    siret CHAR(14) UNIQUE,
    email_domain TEXT,             -- "beta.fr"
    sector TEXT,                   -- "Fintech"
    company_size TEXT
        CHECK (company_size IN ('MICRO', 'SMALL', 'MEDIUM', 'LARGE')),
    address TEXT,
    
    check_siret_found BOOLEAN NOT NULL DEFAULT FALSE,

    verification_status TEXT NOT NULL DEFAULT 'NOT_VERIFIED'
        CHECK (verification_status IN ('NOT_VERIFIED', 'VERIFIED', 'REJECTED')),

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------------
-- Profil recruteur
-- ---------------------------------------------------------------------
CREATE TABLE recruiter_profiles (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE
        REFERENCES users(id) ON DELETE CASCADE,
    company_id BIGINT NOT NULL
        REFERENCES companies(id) ON DELETE RESTRICT,
    first_name TEXT NOT NULL,
    last_name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------------
-- Offres d'emploi
-- ---------------------------------------------------------------------
CREATE TABLE contract_types (
    id SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code TEXT NOT NULL UNIQUE,
    label TEXT NOT NULL
);

CREATE TABLE job_offers (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id BIGINT NOT NULL
        REFERENCES companies(id) ON DELETE RESTRICT,
    recruiter_id BIGINT NOT NULL
        REFERENCES recruiter_profiles(id) ON DELETE RESTRICT,
    contract_type_id SMALLINT NOT NULL
        REFERENCES contract_types(id) ON DELETE RESTRICT,

    title TEXT NOT NULL,
    description TEXT NOT NULL,
    location TEXT,

    salary_min NUMERIC(12, 2),
    salary_max NUMERIC(12, 2),
    salary_currency CHAR(3) NOT NULL DEFAULT 'EUR',
    salary_period TEXT NOT NULL DEFAULT 'YEAR'
        CHECK (salary_period IN ('HOUR', 'MONTH', 'YEAR')),

    remote_mode TEXT NOT NULL DEFAULT 'ONSITE'
        CHECK (remote_mode IN ('ONSITE', 'HYBRID', 'REMOTE')),

    status TEXT NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'CLOSED', 'ARCHIVED')),

    -- si alternance
    rhythm_type TEXT,

    start_date DATE,
    duration_months SMALLINT,
    degree_level_required TEXT
        CHECK (degree_level_required IN ('BAC_2', 'BAC_3', 'BAC_4', 'BAC_5')),
    experience_level_required TEXT
        CHECK (experience_level_required IN ('JUNIOR', 'MID', 'SENIOR')),

    published_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_job_offer_salary
        CHECK (
            salary_min IS NULL
            OR salary_max IS NULL
            OR salary_min <= salary_max
        )
);

-- ---------------------------------------------------------------------
-- Niveaux de compétences
-- ---------------------------------------------------------------------
CREATE TABLE skills (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name TEXT NOT NULL UNIQUE
);

CREATE TABLE job_offer_skills (
    job_offer_id BIGINT NOT NULL
        REFERENCES job_offers(id) ON DELETE CASCADE,
    skill_id BIGINT NOT NULL
        REFERENCES skills(id) ON DELETE RESTRICT,

    required_level TEXT NOT NULL DEFAULT 'BASIC'
        CHECK (required_level IN ('BASIC', 'INTERMEDIATE', 'ADVANCED')),
    
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (job_offer_id, skill_id)
);

CREATE TABLE candidate_skills (
    candidate_id BIGINT NOT NULL
        REFERENCES candidate_profiles(id) ON DELETE CASCADE,
    skill_id BIGINT NOT NULL
        REFERENCES skills(id) ON DELETE RESTRICT,

    level TEXT NOT NULL DEFAULT 'INTERMEDIATE'
        CHECK (level IN ('LEARNING', 'BASIC', 'INTERMEDIATE', 'ADVANCED')),

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (candidate_id, skill_id)
);

INSERT INTO contract_types (code, label) VALUES
    ('CDI', 'CDI'),
    ('CDD', 'CDD'),
    ('INTERNSHIP', 'Stage'),
    ('APPRENTICESHIP', 'Alternance'),
    ('PROFESSIONALIZATION', 'Contrat de professionnalisation'),
    ('FREELANCE', 'Freelance'),
    ('TEMPORARY', 'Intérim');


-- ---------------------------------------------------------------------
-- Swipe dans les deux sens
-- ---------------------------------------------------------------------
CREATE TABLE job_swipes (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    candidate_id BIGINT NOT NULL
        REFERENCES candidate_profiles(id) ON DELETE CASCADE,
    job_offer_id BIGINT NOT NULL
        REFERENCES job_offers(id) ON DELETE CASCADE,
    action TEXT NOT NULL
        CHECK (action IN ('LIKE', 'PASS')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_job_swipe UNIQUE (candidate_id, job_offer_id)
);

-- Recruteur -> candidat, pour une offre donnée
-- (maquette "Candidats compatibles")
CREATE TABLE candidate_swipes (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recruiter_id BIGINT
        REFERENCES recruiter_profiles(id) ON DELETE SET NULL,
    job_offer_id BIGINT NOT NULL
        REFERENCES job_offers(id) ON DELETE CASCADE,
    candidate_id BIGINT NOT NULL
        REFERENCES candidate_profiles(id) ON DELETE CASCADE,
    action TEXT NOT NULL CHECK (action IN ('LIKE', 'PASS')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_candidate_swipe UNIQUE (job_offer_id, candidate_id)
);

-- ---------------------------------------------------------------------
-- Candidatures
-- ---------------------------------------------------------------------
CREATE TABLE application_statuses (
    id SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code TEXT NOT NULL UNIQUE,
    label TEXT NOT NULL
);

CREATE TABLE applications (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    candidate_id BIGINT NOT NULL
        REFERENCES candidate_profiles(id) ON DELETE RESTRICT,
    job_offer_id BIGINT NOT NULL
        REFERENCES job_offers(id) ON DELETE RESTRICT,
    status_id SMALLINT NOT NULL
        REFERENCES application_statuses(id) ON DELETE RESTRICT,

    viewed_at TIMESTAMPTZ,  -- "Vue par le recruteur"
    cover_message TEXT,     -- message envoyé avec la candidature
    resume_url TEXT,        -- CV joint au moment de la candidature

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_application_candidate_offer
        UNIQUE (candidate_id, job_offer_id)
);

INSERT INTO application_statuses (code, label) VALUES
    ('PENDING', 'En attente'),
    ('VIEWED', 'Vue par le recruteur'),
    ('IN_REVIEW', 'En cours d''examen'),
    ('INTERVIEW', 'Entretien'),
    ('ACCEPTED', 'Acceptée'),
    ('REJECTED', 'Refusée'),
    ('WITHDRAWN', 'Retirée');

-- ---------------------------------------------------------------------
-- Favoris / signets
-- ---------------------------------------------------------------------
CREATE TABLE saved_offers (
    candidate_id BIGINT NOT NULL
        REFERENCES candidate_profiles(id) ON DELETE CASCADE,
    job_offer_id BIGINT NOT NULL
        REFERENCES job_offers(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (candidate_id, job_offer_id)
);

-- ---------------------------------------------------------------------
-- Match
-- ---------------------------------------------------------------------
CREATE TABLE matches (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    candidate_id BIGINT NOT NULL
        REFERENCES candidate_profiles(id) ON DELETE CASCADE,
    job_offer_id BIGINT NOT NULL
        REFERENCES job_offers(id) ON DELETE CASCADE,
    compatibility_score SMALLINT
        CHECK (compatibility_score BETWEEN 0 AND 100),
    status TEXT NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'EXPIRED', 'CLOSED')),
    expires_at TIMESTAMPTZ,                   -- expire si pas de réponse (user stories)
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_match UNIQUE (candidate_id, job_offer_id)
);

-- ---------------------------------------------------------------------
-- Messagerie
-- ---------------------------------------------------------------------
CREATE TABLE conversations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id BIGINT UNIQUE
        REFERENCES applications(id) ON DELETE CASCADE,
    match_id BIGINT UNIQUE
        REFERENCES matches(id) ON DELETE CASCADE,
    is_unlocked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_conversation_origin
        CHECK (application_id IS NOT NULL OR match_id IS NOT NULL)
);

CREATE TABLE messages (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id BIGINT NOT NULL
        REFERENCES conversations(id) ON DELETE CASCADE,
    sender_user_id BIGINT
        REFERENCES users(id) ON DELETE SET NULL,
    content TEXT NOT NULL,
    attachment_url TEXT,                      -- CV joint, etc.
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------------
-- Entretiens
-- ---------------------------------------------------------------------
CREATE TABLE interviews (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id BIGINT
        REFERENCES applications(id) ON DELETE CASCADE,
    match_id BIGINT
        REFERENCES matches(id) ON DELETE CASCADE,
    proposed_by BIGINT
        REFERENCES users(id) ON DELETE SET NULL,
    scheduled_at TIMESTAMPTZ NOT NULL,
    duration_minutes SMALLINT NOT NULL DEFAULT 30,
    mode TEXT NOT NULL DEFAULT 'VIDEO'
        CHECK (mode IN ('ONSITE', 'VIDEO', 'PHONE')),
    location_or_link TEXT,
    status TEXT NOT NULL DEFAULT 'PROPOSED'
        CHECK (status IN ('PROPOSED', 'CONFIRMED', 'DECLINED', 'CANCELLED', 'DONE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_interview_origin
        CHECK (application_id IS NOT NULL OR match_id IS NOT NULL)
);

-- ---------------------------------------------------------------------
-- Cooptation
-- ---------------------------------------------------------------------
CREATE TABLE recommendations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    referrer_id BIGINT NOT NULL
        REFERENCES candidate_profiles(id) ON DELETE CASCADE,
    candidate_id BIGINT NOT NULL
        REFERENCES candidate_profiles(id) ON DELETE CASCADE,
    job_offer_id BIGINT
        REFERENCES job_offers(id) ON DELETE SET NULL,   -- facultatif
    requested_by TEXT NOT NULL
        CHECK (requested_by IN ('CANDIDATE', 'REFERRER')),
    status TEXT NOT NULL DEFAULT 'REQUESTED'
        CHECK (status IN ('REQUESTED', 'ACCEPTED', 'DECLINED', 'SENT')),
    message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------------
-- Notifications
-- ---------------------------------------------------------------------
CREATE TABLE notifications (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL
        REFERENCES users(id) ON DELETE CASCADE,
    type TEXT NOT NULL
        CHECK (type IN ('NEW_MATCH', 'NEW_MESSAGE', 'APPLICATION_UPDATE',
                        'INTERVIEW', 'NEW_OFFER', 'RECOMMENDATION', 'SYSTEM')),
    title TEXT NOT NULL,
    link TEXT,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Préférences de notification (paramètres)
CREATE TABLE notification_preferences (
    user_id BIGINT PRIMARY KEY
        REFERENCES users(id) ON DELETE CASCADE,
    email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    push_enabled  BOOLEAN NOT NULL DEFAULT TRUE,
    new_offer_alerts BOOLEAN NOT NULL DEFAULT TRUE
);

-- ---------------------------------------------------------------------
-- 15. Admin
-- ---------------------------------------------------------------------
CREATE TABLE reports (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reporter_user_id BIGINT
        REFERENCES users(id) ON DELETE SET NULL,
    target_type TEXT NOT NULL
        CHECK (target_type IN ('USER', 'JOB_OFFER', 'MESSAGE')),
    target_id BIGINT NOT NULL,                -- id dans la table visée (vérifié par le backend)
    reason TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'IGNORED', 'ACTION_TAKEN')),
    handled_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    handled_at TIMESTAMPTZ
);


-- ---------------------------------------------------------------------
-- Vue pratique : statistiques de réponse des entreprises
-- ---------------------------------------------------------------------
CREATE VIEW company_response_stats AS
SELECT
    o.company_id,
    COUNT(a.id)                                         AS applications_count,
    COUNT(a.id) FILTER (WHERE s.code <> 'PENDING')      AS answered_count,
    ROUND(100.0 * COUNT(a.id) FILTER (WHERE s.code <> 'PENDING')
          / NULLIF(COUNT(a.id), 0))                     AS response_rate_pct,
    ROUND(AVG(EXTRACT(EPOCH FROM (a.updated_at - a.created_at)) / 86400.0)
          FILTER (WHERE s.code <> 'PENDING'), 1)        AS avg_response_days
FROM job_offers o
JOIN applications a        ON a.job_offer_id = o.id
JOIN application_statuses s ON s.id = a.status_id
GROUP BY o.company_id;



-- ---------------------------------------------------------------------
-- Index sur les clés étrangères (PostgreSQL ne les crée pas seul)
-- ---------------------------------------------------------------------
CREATE INDEX idx_auth_tokens_user             ON auth_tokens (user_id);
CREATE INDEX idx_recruiter_profiles_company   ON recruiter_profiles (company_id);
CREATE INDEX idx_job_offers_company           ON job_offers (company_id);
CREATE INDEX idx_job_offers_recruiter         ON job_offers (recruiter_id);
CREATE INDEX idx_job_offers_status            ON job_offers (status);
CREATE INDEX idx_job_offer_skills_skill       ON job_offer_skills (skill_id);
CREATE INDEX idx_candidate_skills_skill       ON candidate_skills (skill_id);
CREATE INDEX idx_job_swipes_offer             ON job_swipes (job_offer_id);
CREATE INDEX idx_candidate_swipes_candidate   ON candidate_swipes (candidate_id);
CREATE INDEX idx_saved_offers_offer           ON saved_offers (job_offer_id);
CREATE INDEX idx_applications_offer           ON applications (job_offer_id);
CREATE INDEX idx_matches_offer                ON matches (job_offer_id);
CREATE INDEX idx_messages_conversation        ON messages (conversation_id, created_at);
CREATE INDEX idx_interviews_application       ON interviews (application_id);
CREATE INDEX idx_interviews_match             ON interviews (match_id);
CREATE INDEX idx_recommendations_candidate    ON recommendations (candidate_id);
CREATE INDEX idx_recommendations_referrer     ON recommendations (referrer_id);
CREATE INDEX idx_notifications_user_unread    ON notifications (user_id) WHERE read_at IS NULL;
CREATE INDEX idx_reports_status               ON reports (status);

-- ---------------------------------------------------------------------
-- Triggers updated_at
-- ---------------------------------------------------------------------
CREATE TRIGGER trg_users_updated              BEFORE UPDATE ON users              FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_companies_updated          BEFORE UPDATE ON companies          FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_candidate_profiles_updated BEFORE UPDATE ON candidate_profiles FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_recruiter_profiles_updated BEFORE UPDATE ON recruiter_profiles FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_job_offers_updated         BEFORE UPDATE ON job_offers         FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_applications_updated       BEFORE UPDATE ON applications       FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_interviews_updated         BEFORE UPDATE ON interviews         FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_recommendations_updated    BEFORE UPDATE ON recommendations    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
