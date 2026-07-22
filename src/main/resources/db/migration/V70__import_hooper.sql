-- ============================================================
-- V70 — Import staff du ressenti quotidien (indice de Hooper)
--
-- Les clubs dont les joueurs n'utilisent pas encore la PWA fournissent leur
-- ressenti quotidien via un export « playermonitoring » (CSV/xlsx). Le staff
-- l'importe, par équipe, sur le modèle des imports GPS/RPE :
--   · POST /api/import-hooper/analyser  → lecture + liaison des noms (aperçu)
--   · POST /api/import-hooper/confirmer → upsert wellness_quotidien (clé joueur+date)
--
-- Aucun nouveau module ni colonne : l'import est gouverné par le module `wellness`
-- (« Ressenti & RPE ») via la permission hooper:import (FeatureModule.of), et vise
-- la table wellness_quotidien existante. La liaison des noms réutilise
-- alias_joueur_import (par club), comme l'import RPE.
--
-- Conventions de conversion (côté service) :
--   · l'export note 10 = bon / 1 = mauvais sur les items positifs ; l'app est
--     l'inverse (1 = bon → 10 = mauvais) → inversion 11 − valeur ;
--   · stress non demandé dans l'export → valeur neutre 5 à la création ;
--   · douleur localisée (Emplacement + Douleur) → gene_zone / gene_intensite,
--     SANS alerte médicale (import de masse).
-- ============================================================
SET search_path = public;

-- Permission d'import Hooper : mêmes rôles que rpe:import / gps:import — entraîneur
-- chef (…001) et préparateur physique (…003). SUPER_ADMIN reste un bypass global.
INSERT INTO role_permission (role_id, permission) VALUES
 ('a0000000-0000-0000-0000-000000000001', 'hooper:import'),
 ('a0000000-0000-0000-0000-000000000003', 'hooper:import')
ON CONFLICT (role_id, permission) DO NOTHING;
