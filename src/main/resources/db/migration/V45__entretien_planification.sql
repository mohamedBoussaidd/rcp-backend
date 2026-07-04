-- ============================================================
-- V45 — Planification des entretiens (RDV au calendrier)
--
-- Un entretien devient un objet à cycle de vie, comme une séance :
--   PLANIFIE : rendez-vous à venir (date, heure facultative, notes de préparation),
--              visible au calendrier staff + PWA joueur, notifié au joueur.
--   REALISE  : compte-rendu (comportement historique) — seul statut compté dans
--              les agrégats (synthèse, vue équipe, alerte « sans entretien récent »).
--
-- La planification est OPTIONNELLE : le flux direct « compte-rendu maintenant »
-- reste le défaut (DEFAULT 'REALISE' → existants grandfathered, zéro régression).
-- Visibilité agenda ≠ visibilité contenu : le RDV est montré au joueur, les
-- notes/notations restent STAFF jusqu'au partage explicite (PARTAGE_JOUEUR).
-- ============================================================
SET search_path = public;

ALTER TABLE entretien
    ADD COLUMN statut varchar(12) NOT NULL DEFAULT 'REALISE',   -- 'PLANIFIE' | 'REALISE'
    ADD COLUMN heure  time;                                     -- facultative (jour seul possible)

-- Agenda : lookup par équipe + période (calendrier staff), et par joueur + statut (PWA).
CREATE INDEX idx_entretien_equipe_statut_date ON entretien (equipe_id, statut, date_entretien);
