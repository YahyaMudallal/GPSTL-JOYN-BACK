-- ---------------------------------------------------------------------
-- Profil candidat : champs manquants pour CA56 (lecture) et CA68 (mise à jour)
-- ---------------------------------------------------------------------
ALTER TABLE candidate_profiles
    -- Coordonnées modifiables par le candidat (CA68).
    -- Format international E.164 (ex. +33612345678) : le backend valide déjà,
    -- la contrainte protège aussi des écritures faites hors de l'API.
    ADD COLUMN phone TEXT
        CHECK (phone ~ '^\+[1-9][0-9]{6,14}$'),
    ADD COLUMN years_of_experience SMALLINT
        CHECK (years_of_experience >= 0),

    -- Photo de profil renvoyée par GET (CA56) : on stocke seulement l'URL,
    -- le fichier reste dans le stockage média, comme resume_url.
    ADD COLUMN photo_url TEXT,

    -- Préférences de recherche (relation 1-1 avec le profil, donc stockées ici :
    -- pas de jointure en lecture et un seul verrou de ligne lors de la mise à jour).
    -- Mêmes valeurs et mêmes types que job_offers (remote_mode, salary_min,
    -- salary_currency) pour pouvoir comparer directement lors du matching.
    ADD COLUMN pref_remote_policy TEXT
        CHECK (pref_remote_policy IN ('ONSITE', 'HYBRID', 'REMOTE')),
    ADD COLUMN pref_min_salary NUMERIC(12, 2)
        CHECK (pref_min_salary >= 0),
    ADD COLUMN pref_salary_currency CHAR(3) NOT NULL DEFAULT 'EUR';

-- ---------------------------------------------------------------------
-- Types de contrat recherchés par le candidat (choix multiple)
-- ---------------------------------------------------------------------
-- Table de jointure plutôt qu'une colonne TEXT[] :
--  - la liste des types reste définie à un seul endroit (contract_types),
--    ajouter un type ne demande pas de modifier une contrainte CHECK ;
--  - la clé étrangère garantit qu'un type inexistant ne peut pas être enregistré ;
--  - job_offers référence aussi contract_types par id : le matching peut
--    joindre directement les préférences du candidat et les offres.
CREATE TABLE candidate_preferred_contract_types (
    candidate_id BIGINT NOT NULL
        REFERENCES candidate_profiles(id) ON DELETE CASCADE,
    contract_type_id SMALLINT NOT NULL
        REFERENCES contract_types(id) ON DELETE RESTRICT,
    PRIMARY KEY (candidate_id, contract_type_id)
);

-- Index sur la clé étrangère (PostgreSQL ne le crée pas seul) ;
-- la clé primaire couvre déjà les recherches par candidate_id.
CREATE INDEX idx_candidate_pref_contracts_type
    ON candidate_preferred_contract_types (contract_type_id);

-- ---------------------------------------------------------------------
-- Catalogue de compétences : unicité insensible à la casse
-- ---------------------------------------------------------------------
-- Le backend cherche les compétences sans tenir compte de la casse, mais
-- UNIQUE (name) distingue « Java » et « java ». Deux requêtes simultanées
-- pouvaient donc créer les deux, et la recherche renvoyait ensuite deux lignes.
-- Cet index sert aussi de cible à INSERT ... ON CONFLICT ((LOWER(name))) DO NOTHING,
-- qui rend la création « chercher ou créer » sûre en cas d'accès concurrents.
--
-- Règle obtenue : l'unicité ne tient plus compte de la casse (« Java » et « java » ne
-- peuvent plus coexister). UNIQUE (name) de V1 est conservée : elle est désormais
-- couverte par cet index, mais inoffensive. Le nom garde l'écriture de sa première
-- saisie (« Java » reste « Java ») ; une saisie « java » réutilise cette compétence
-- au lieu d'en créer une nouvelle, et c'est « Java » qui est affiché.
--
-- Une base existante peut déjà contenir de tels doublons : l'index échouerait alors et
-- bloquerait la migration (donc le démarrage). Ces doublons cassent aussi déjà
-- findByNameIgnoreCase, qui attend au plus une ligne. On les fusionne d'abord :
--  - on garde, pour chaque nom, la compétence au plus petit id ;
--  - les liens des offres et des candidats sont reportés sur elle ; si une offre ou un
--    candidat avait plusieurs variantes, on garde le niveau le plus élevé ;
--  - les variantes, devenues inutilisées, sont supprimées (les clés étrangères sont
--    en ON DELETE RESTRICT : il faut déplacer les liens avant).
-- Sans doublon, ces requêtes ne modifient rien.
CREATE TEMPORARY TABLE skill_merge ON COMMIT DROP AS
SELECT s.id AS duplicate_id, k.keep_id
FROM skills s
JOIN (SELECT LOWER(name) AS name_key, MIN(id) AS keep_id
      FROM skills GROUP BY LOWER(name) HAVING COUNT(*) > 1) k
  ON LOWER(s.name) = k.name_key
WHERE s.id <> k.keep_id;

INSERT INTO job_offer_skills (job_offer_id, skill_id, required_level)
SELECT d.job_offer_id, m.keep_id,
       (ARRAY['BASIC', 'INTERMEDIATE', 'ADVANCED'])[
           MAX(array_position(ARRAY['BASIC', 'INTERMEDIATE', 'ADVANCED'], d.required_level))]
FROM skill_merge m
JOIN job_offer_skills d ON d.skill_id = m.duplicate_id
GROUP BY d.job_offer_id, m.keep_id
ON CONFLICT (job_offer_id, skill_id) DO UPDATE
SET required_level = CASE
    WHEN array_position(ARRAY['BASIC', 'INTERMEDIATE', 'ADVANCED'], EXCLUDED.required_level)
       > array_position(ARRAY['BASIC', 'INTERMEDIATE', 'ADVANCED'], job_offer_skills.required_level)
    THEN EXCLUDED.required_level ELSE job_offer_skills.required_level END;

DELETE FROM job_offer_skills WHERE skill_id IN (SELECT duplicate_id FROM skill_merge);

INSERT INTO candidate_skills (candidate_id, skill_id, level)
SELECT d.candidate_id, m.keep_id,
       (ARRAY['LEARNING', 'BASIC', 'INTERMEDIATE', 'ADVANCED'])[
           MAX(array_position(ARRAY['LEARNING', 'BASIC', 'INTERMEDIATE', 'ADVANCED'], d.level))]
FROM skill_merge m
JOIN candidate_skills d ON d.skill_id = m.duplicate_id
GROUP BY d.candidate_id, m.keep_id
ON CONFLICT (candidate_id, skill_id) DO UPDATE
SET level = CASE
    WHEN array_position(ARRAY['LEARNING', 'BASIC', 'INTERMEDIATE', 'ADVANCED'], EXCLUDED.level)
       > array_position(ARRAY['LEARNING', 'BASIC', 'INTERMEDIATE', 'ADVANCED'], candidate_skills.level)
    THEN EXCLUDED.level ELSE candidate_skills.level END;

DELETE FROM candidate_skills WHERE skill_id IN (SELECT duplicate_id FROM skill_merge);

DELETE FROM skills WHERE id IN (SELECT duplicate_id FROM skill_merge);

CREATE UNIQUE INDEX uq_skills_name_lower ON skills (LOWER(name));

-- ---------------------------------------------------------------------
-- Journal des modifications de profil faites par un administrateur
-- ---------------------------------------------------------------------
-- Un candidat qui modifie son propre profil n'est pas journalisé ; un administrateur
-- qui intervient sur le profil d'un tiers l'est (qui, quand, quels champs).
--  - actor_user_id sans clé étrangère : la trace reste si le compte admin disparaît ;
--  - ON DELETE CASCADE sur le profil : supprimer un candidat (droit à l'effacement)
--    supprime aussi les traces qui le concernent.
CREATE TABLE candidate_profile_audit (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_user_id BIGINT NOT NULL,
    candidate_id BIGINT NOT NULL
        REFERENCES candidate_profiles(id) ON DELETE CASCADE,
    action TEXT NOT NULL
        CHECK (action IN ('ADMIN_UPDATE')),
    changed_fields TEXT NOT NULL,             -- chemins JSON séparés par des virgules
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_candidate_profile_audit_candidate
    ON candidate_profile_audit (candidate_id, created_at);
