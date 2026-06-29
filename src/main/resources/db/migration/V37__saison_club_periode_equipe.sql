-- ============================================================
-- V37 — PIVOT : Saison au niveau CLUB, Périodes & Effectif au niveau ÉQUIPE
--
-- V36 plaçait la saison au niveau ÉQUIPE (saison.equipe_id). On bascule :
--   saison           : une saison par CLUB (« 2026-2027 » partagée par toutes les équipes)
--   periode_saison   : périodes typées PAR ÉQUIPE dans la saison (clé saison_id + equipe_id)
--   effectif_saison  : appartenance PAR ÉQUIPE (clé saison_id + equipe_id + joueur_id)
--
-- Migration de données :
--   1. Porter club_id sur saison (via equipe.club_id) et equipe_id sur les tables filles
--      AVANT de supprimer saison.equipe_id.
--   2. Fusionner les saisons en double (même club_id + même libellé venant de 2 équipes)
--      en une seule saison canonique.
--   3. Garantir au plus UNE saison EN_COURS par club.
-- ============================================================
SET search_path = public;

-- ── 1) Nouvelles colonnes + backfill (avant toute suppression) ──────────────
ALTER TABLE saison          ADD COLUMN club_id   uuid;
ALTER TABLE periode_saison  ADD COLUMN equipe_id uuid;
ALTER TABLE effectif_saison ADD COLUMN equipe_id uuid;

UPDATE saison s
   SET club_id = e.club_id
  FROM equipe e
 WHERE e.id = s.equipe_id;

UPDATE periode_saison p
   SET equipe_id = s.equipe_id
  FROM saison s
 WHERE s.id = p.saison_id;

UPDATE effectif_saison ef
   SET equipe_id = s.equipe_id
  FROM saison s
 WHERE s.id = ef.saison_id;

-- ── 2) Lever les contraintes qui gênent la fusion / re-bascule ──────────────
-- Unique « (saison_id, joueur_id) » : deux équipes différentes peuvent partager
-- la même saison après fusion → on passe à (saison_id, equipe_id, joueur_id) plus bas.
ALTER TABLE effectif_saison DROP CONSTRAINT uq_effectif_saison;
-- Unique partiel « une EN_COURS par équipe » : remplacé par « une EN_COURS par club ».
DROP INDEX IF EXISTS uq_saison_en_cours;

-- ── 3) Fusion des saisons en double (même club + même libellé) ──────────────
-- Saison canonique = la plus ancienne (created_at) pour chaque (club_id, libelle).
CREATE TEMP TABLE saison_merge ON COMMIT DROP AS
SELECT s.id AS dup_id,
       (SELECT s2.id
          FROM saison s2
         WHERE s2.club_id = s.club_id AND s2.libelle = s.libelle
         ORDER BY s2.created_at ASC
         LIMIT 1) AS canon_id
  FROM saison s;

UPDATE periode_saison p
   SET saison_id = m.canon_id
  FROM saison_merge m
 WHERE p.saison_id = m.dup_id AND m.dup_id <> m.canon_id;

UPDATE effectif_saison ef
   SET saison_id = m.canon_id
  FROM saison_merge m
 WHERE ef.saison_id = m.dup_id AND m.dup_id <> m.canon_id;

DELETE FROM saison s
 USING saison_merge m
 WHERE s.id = m.dup_id AND m.dup_id <> m.canon_id;

-- ── 4) Au plus UNE saison EN_COURS par club ─────────────────────────────────
-- (cas où deux équipes d'un même club avaient chacune une EN_COURS de libellés différents).
-- On garde la plus récente (date_debut), on clôture les autres.
UPDATE saison s
   SET statut = 'CLOTUREE'
 WHERE s.statut = 'EN_COURS'
   AND s.id <> (
        SELECT s2.id
          FROM saison s2
         WHERE s2.club_id = s.club_id AND s2.statut = 'EN_COURS'
         ORDER BY s2.date_debut DESC, s2.created_at DESC
         LIMIT 1);

-- ── 5) saison : finaliser club_id, retirer equipe_id ────────────────────────
ALTER TABLE saison ALTER COLUMN club_id SET NOT NULL;
ALTER TABLE saison ADD CONSTRAINT saison_club_fkey
      FOREIGN KEY (club_id) REFERENCES club(id) ON DELETE CASCADE;

ALTER TABLE saison DROP CONSTRAINT saison_equipe_fkey;
DROP INDEX IF EXISTS idx_saison_equipe;
ALTER TABLE saison DROP COLUMN equipe_id;

CREATE INDEX idx_saison_club ON saison (club_id);
-- Au plus UNE saison EN_COURS par club.
CREATE UNIQUE INDEX uq_saison_en_cours ON saison (club_id) WHERE statut = 'EN_COURS';

-- ── 6) periode_saison : equipe_id obligatoire ───────────────────────────────
ALTER TABLE periode_saison ALTER COLUMN equipe_id SET NOT NULL;
ALTER TABLE periode_saison ADD CONSTRAINT periode_equipe_fkey
      FOREIGN KEY (equipe_id) REFERENCES equipe(id) ON DELETE CASCADE;
CREATE INDEX idx_periode_saison_equipe ON periode_saison (saison_id, equipe_id);

-- ── 7) effectif_saison : equipe_id obligatoire + nouvelle unicité ───────────
ALTER TABLE effectif_saison ALTER COLUMN equipe_id SET NOT NULL;
ALTER TABLE effectif_saison ADD CONSTRAINT effectif_saison_equipe_fkey
      FOREIGN KEY (equipe_id) REFERENCES equipe(id) ON DELETE CASCADE;
ALTER TABLE effectif_saison ADD CONSTRAINT uq_effectif_saison
      UNIQUE (saison_id, equipe_id, joueur_id);
CREATE INDEX idx_effectif_saison_equipe ON effectif_saison (saison_id, equipe_id);
