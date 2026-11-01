-- ---------------------------------------------------------------------
-- Listes du back-office admin (A80) : filtre par rôle et statut, tri par date d'inscription
-- ---------------------------------------------------------------------
CREATE INDEX idx_users_role_status ON users (role, status);
CREATE INDEX idx_users_created_at ON users (created_at DESC);
