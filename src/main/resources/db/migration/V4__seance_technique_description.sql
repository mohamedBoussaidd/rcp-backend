-- ============================================================
-- V4 — Ajout d'une description libre sur la seance technique
-- ============================================================
SET search_path = public;

ALTER TABLE seance_technique ADD COLUMN description text;
