-- Migration V10: Gestion des photos de profil pour tous les utilisateurs
-- Stockage de la clé d'objet du bucket Supabase (profile_pictures/{userId}/{uuid}.webp) ou URL externe

ALTER TABLE users ADD COLUMN profile_picture_url TEXT;

-- Initialisation depuis les photos de profil candidat existantes si renseignées
UPDATE users u
SET profile_picture_url = cp.photo_url
FROM candidate_profiles cp
WHERE cp.user_id = u.id AND cp.photo_url IS NOT NULL;
