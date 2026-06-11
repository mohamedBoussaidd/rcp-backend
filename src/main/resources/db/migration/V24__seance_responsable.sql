-- ============================================================
-- V24 — Responsable (encadrant) d'une séance
-- Nom affiché de l'encadrant en charge de la séance (texte libre),
-- montré dans la vue Liste du planning.
-- ============================================================
SET search_path = public;

ALTER TABLE seance ADD COLUMN responsable varchar(100);
