-- ============================================================
-- V38 — Cascade de suppression club / équipe
--
-- Supprimer un club (ou une équipe) repose sur les cascades FK : club → équipe → joueur/séance
-- → … Or plusieurs tables référençaient joueur/séance en NO ACTION, ce qui BLOQUAIT la
-- suppression (ex. « donnee_gps_joueur_id_fkey » : la fiche joueur est encore référencée).
--
-- On passe ces FK en ON DELETE CASCADE pour que la suppression d'un club/équipe efface
-- proprement les données rattachées. Sans impact sur la rétention « ML » : aucun endpoint ne
-- supprime une fiche joueur isolément (un transfert ne supprime pas la fiche, il sort le joueur
-- de l'effectif de saison) — la cascade ne se déclenche qu'à la suppression d'une équipe/d'un club.
-- ============================================================
SET search_path = public;

-- ── Enfants de joueur : effacés avec la fiche ──────────────────────────────
ALTER TABLE agregat_joueur  DROP CONSTRAINT agregat_joueur_joueur_id_fkey;
ALTER TABLE agregat_joueur  ADD  CONSTRAINT agregat_joueur_joueur_id_fkey
      FOREIGN KEY (joueur_id) REFERENCES joueur(id) ON DELETE CASCADE;

ALTER TABLE baseline_joueur DROP CONSTRAINT baseline_joueur_joueur_id_fkey;
ALTER TABLE baseline_joueur ADD  CONSTRAINT baseline_joueur_joueur_id_fkey
      FOREIGN KEY (joueur_id) REFERENCES joueur(id) ON DELETE CASCADE;

ALTER TABLE blessure       DROP CONSTRAINT blessure_joueur_id_fkey;
ALTER TABLE blessure       ADD  CONSTRAINT blessure_joueur_id_fkey
      FOREIGN KEY (joueur_id) REFERENCES joueur(id) ON DELETE CASCADE;

ALTER TABLE donnee_gps     DROP CONSTRAINT donnee_gps_joueur_id_fkey;
ALTER TABLE donnee_gps     ADD  CONSTRAINT donnee_gps_joueur_id_fkey
      FOREIGN KEY (joueur_id) REFERENCES joueur(id) ON DELETE CASCADE;

ALTER TABLE recommandation DROP CONSTRAINT recommandation_joueur_id_fkey;
ALTER TABLE recommandation ADD  CONSTRAINT recommandation_joueur_id_fkey
      FOREIGN KEY (joueur_id) REFERENCES joueur(id) ON DELETE CASCADE;

-- ── Enfant de séance : les GPS suivent la séance supprimée ─────────────────
ALTER TABLE donnee_gps     DROP CONSTRAINT donnee_gps_seance_id_fkey;
ALTER TABLE donnee_gps     ADD  CONSTRAINT donnee_gps_seance_id_fkey
      FOREIGN KEY (seance_id) REFERENCES seance(id) ON DELETE CASCADE;

-- ── Auto-référence des blessures : on dénoue le lien (pas de suppression en chaîne) ──
ALTER TABLE blessure       DROP CONSTRAINT blessure_blessure_precedente_id_fkey;
ALTER TABLE blessure       ADD  CONSTRAINT blessure_blessure_precedente_id_fkey
      FOREIGN KEY (blessure_precedente_id) REFERENCES blessure(id) ON DELETE SET NULL;
