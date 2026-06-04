-- ============================================================
-- V7 — Schéma tactique (tableau) rattaché à un exercice
-- Stocke en JSON : terrain, éléments (joueurs/équipement), tracés,
-- et keyframes (Phase B animation). 1 keyframe = schéma statique.
-- ============================================================
SET search_path = public;

ALTER TABLE exercice ADD COLUMN schema_json text;
