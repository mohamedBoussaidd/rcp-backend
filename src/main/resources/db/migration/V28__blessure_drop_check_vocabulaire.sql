-- ============================================================
-- V28 — Vocabulaire des blessures piloté par l'application
--
-- Le module Blessures (refonte) gère désormais le vocabulaire côté front
-- (zones / types / causes via des pickers typés). Les CHECK constraints
-- figées en V1 bloquent les nouvelles valeurs (ex. cause 'entrainement',
-- type 'contusion', zone 'tendon_achille'…) et coexisteraient mal avec les
-- anciennes valeurs historiques. On retire ces 3 contraintes volatiles.
--
-- cote / gravite conservent leur contrainte (jeux de valeurs stables).
-- ============================================================
SET search_path = public;

ALTER TABLE blessure DROP CONSTRAINT IF EXISTS blessure_cause_probable_check;
ALTER TABLE blessure DROP CONSTRAINT IF EXISTS blessure_type_blessure_check;
ALTER TABLE blessure DROP CONSTRAINT IF EXISTS blessure_zone_corporelle_check;
