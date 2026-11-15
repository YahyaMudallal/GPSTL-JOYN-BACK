-- ---------------------------------------------------------------------
-- Migration V10 : Améliorations de la gestion des notifications
-- ---------------------------------------------------------------------

-- 1. Ajout de la colonne message pour le contenu textuel / résumé de la notification
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS message TEXT;

-- 2. Mise à jour de la contrainte CHECK sur le type pour inclure NEW_APPLICATION
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('NEW_MATCH', 'NEW_MESSAGE', 'APPLICATION_UPDATE', 'NEW_APPLICATION',
                    'INTERVIEW', 'NEW_OFFER', 'RECOMMENDATION', 'SYSTEM'));

-- 3. Index optimisés pour les requêtes par utilisateur triées par date et le nettoyage automatique
CREATE INDEX IF NOT EXISTS idx_notifications_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_created_at ON notifications (created_at);
