-- ============================================================
-- V69 — Import staff du RPE/ressenti + colonne « plaisir » sur le RPE de séance
--
-- Les clubs dont les joueurs n'utilisent pas encore la PWA fournissent leur
-- RPE via des exports CSV (fichier questionnaire post-séance). Le staff les
-- importe, séance par séance, sur le modèle de l'import GPS :
--   · POST /api/import-rpe/analyser  → lecture + liaison des noms (aperçu)
--   · POST /api/import-rpe/confirmer → upsert rpe_seance (charge = rpe × durée)
--
-- Aucun nouveau module : l'import est gouverné par le module `wellness`
-- (« Ressenti & RPE ») via la permission rpe:import (FeatureModule.of).
-- La liaison des noms réutilise alias_joueur_import (par club).
--
--  · rpe_seance.plaisir : niveau de plaisir ressenti sur la séance (1..10),
--    saisi dans le même questionnaire post-séance. Nullable (absent des
--    saisies PWA existantes et des lignes sans réponse).
-- ============================================================
SET search_path = public;

ALTER TABLE rpe_seance ADD COLUMN plaisir smallint;

-- Permission d'import RPE : mêmes rôles que gps:import (V30) — entraîneur chef
-- (…001) et préparateur physique (…003). SUPER_ADMIN reste un bypass global.
INSERT INTO role_permission (role_id, permission) VALUES
 ('a0000000-0000-0000-0000-000000000001', 'rpe:import'),
 ('a0000000-0000-0000-0000-000000000003', 'rpe:import')
ON CONFLICT (role_id, permission) DO NOTHING;
