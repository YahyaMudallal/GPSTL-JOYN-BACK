-- Compte administrateur par défaut, pour le DEVELOPPEMENT uniquement (U16).
-- Email : admin@joyn.test
-- Mot de passe : JoynAdmin2026!   (haché ci-dessous avec BCrypt, coût 10)
INSERT INTO users (email, password_hash, role, status)
VALUES ('admin@joyn.test',
        '$2a$10$kwujKf5ufi/QNPgWSQWrN.LeSHzhp4qeNBrtMQhhVvpw/x/qQxY7e',
        'ADMIN',
        'ACTIVE')
ON CONFLICT DO NOTHING;


