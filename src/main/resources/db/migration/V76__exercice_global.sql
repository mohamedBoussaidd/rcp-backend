-- Bibliothèque d'exercices globale (CB) : un exercice peut désormais n'appartenir à aucun club
-- (club_id NULL = exercice global créé par le super-admin, visible par tous les clubs en lecture).
ALTER TABLE exercice ALTER COLUMN club_id DROP NOT NULL;
