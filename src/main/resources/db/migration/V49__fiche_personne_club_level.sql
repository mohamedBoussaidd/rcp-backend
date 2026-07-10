-- ============================================================
-- V49 — Fiche « personne » au niveau club (Phase 1 : fondation, coexistence)
--
-- Élargit la fiche `joueur` (qui devient la fiche unifiée d'une PERSONNE du club, joueur ET/OU
-- staff) : on lui donne un rattachement CLUB direct (`club_id`), au lieu de dériver son club de
-- son unique équipe. L'appartenance à une (ou plusieurs) équipe reste gérée par `effectif_saison`
-- (joueurs) et `affectation_role` (staff). `equipe_id` est CONSERVÉ ici comme cache legacy
-- (« équipe principale ») le temps de la transition — il sera retiré dans une phase ultérieure.
--
-- Une fiche peut désormais exister au niveau club SANS équipe (recrue non assignée, joueur en
-- transit, membre du staff). C'est ce qui débloque la création/liaison de fiche par un
-- président/administratif (club-wide, sans équipe propre).
--
-- Coexistence : rien n'est supprimé, aucun scoping existant n'est cassé (equipe_id reste lu).
-- ============================================================
SET search_path = public;

-- ── club_id sur la fiche ────────────────────────────────────────────────────
ALTER TABLE joueur ADD COLUMN club_id uuid;

-- Backfill depuis l'équipe actuelle (pour les fiches déjà rattachées à une équipe).
UPDATE joueur j
   SET club_id = e.club_id
  FROM equipe e
 WHERE e.id = j.equipe_id
   AND j.club_id IS NULL;

ALTER TABLE joueur
    ADD CONSTRAINT joueur_club_fkey FOREIGN KEY (club_id) REFERENCES club(id) ON DELETE CASCADE;

CREATE INDEX idx_joueur_club ON joueur (club_id);

-- ── Référentiel documents : cible (public concerné) — préparation conformité STAFF ──
-- 'JOUEUR' (défaut, comportement actuel : filtré par catégorie d'âge) | 'STAFF' | 'TOUS'.
ALTER TABLE type_document_requis
    ADD COLUMN cible varchar(10) NOT NULL DEFAULT 'JOUEUR';
ALTER TABLE type_document_requis
    ADD CONSTRAINT type_document_requis_cible_chk CHECK (cible IN ('JOUEUR','STAFF','TOUS'));

-- Types de documents STAFF par défaut, pour tous les clubs existants (obligatoires en France :
-- licence dirigeant/éducateur, diplôme d'encadrement, contrôle d'honorabilité).
INSERT INTO type_document_requis (club_id, code, libelle, description, obligatoire, validation_manuelle, duree_validite_mois, categories_age, cible, ordre)
SELECT c.id, v.code, v.libelle, v.description, v.obligatoire, v.validation_manuelle, v.duree_validite_mois, NULL, 'STAFF', v.ordre
FROM club c
CROSS JOIN (VALUES
    ('licence_dirigeant', 'Licence dirigeant/éducateur',
     'Licence fédérale d''encadrant de la saison en cours',
     true, true, 12::smallint, 20::smallint),
    ('diplome_encadrement', 'Diplôme d''encadrement',
     'Diplôme d''entraîneur / éducateur (BEF, BMF, CFF…)',
     false, true, NULL::smallint, 21::smallint),
    ('honorabilite', 'Contrôle d''honorabilité',
     'Attestation de contrôle d''honorabilité (obligatoire pour tout encadrant)',
     true, true, 12::smallint, 22::smallint)
) AS v(code, libelle, description, obligatoire, validation_manuelle, duree_validite_mois, ordre);
