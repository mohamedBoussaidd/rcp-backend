-- ============================================================
-- V27 — Refonte module Blessures
--
-- blessure.notes_medicales : second champ texte, réservé au staff médical
--                            (distinct de "commentaire", contexte général).
-- rtp_etape : enrichissement des phases du protocole de retour au jeu
--             - j_debut / j_fin : fenêtre indicative en jours (ex. J1–J5)
--             - description     : détail des actions de la phase
-- ============================================================
SET search_path = public;

ALTER TABLE blessure  ADD COLUMN notes_medicales text;

ALTER TABLE rtp_etape ADD COLUMN j_debut     smallint;
ALTER TABLE rtp_etape ADD COLUMN j_fin       smallint;
ALTER TABLE rtp_etape ADD COLUMN description text;
