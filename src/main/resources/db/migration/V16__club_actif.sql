-- ============================================================
-- V16 — État d'activité d'un club
--
-- Indicateur d'administration : un club peut être actif (par défaut) ou
-- désactivé (archivé) par le super-admin, sans suppression des données.
-- ============================================================
SET search_path = public;

ALTER TABLE club ADD COLUMN actif boolean NOT NULL DEFAULT true;
