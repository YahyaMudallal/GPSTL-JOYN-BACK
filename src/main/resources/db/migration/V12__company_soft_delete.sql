-- Migration V12 : suppression logique des entreprises
-- Une entreprise supprimée garde sa ligne (status = 'DELETED', deleted_at renseigné) :
-- les offres, candidatures et messages qui la référencent restent intacts.

ALTER TABLE companies
    ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'DELETED'));

ALTER TABLE companies ADD COLUMN deleted_at TIMESTAMPTZ;

-- Cohérence entre les deux colonnes
ALTER TABLE companies
    ADD CONSTRAINT chk_companies_deleted_at
        CHECK ((status = 'DELETED') = (deleted_at IS NOT NULL));
