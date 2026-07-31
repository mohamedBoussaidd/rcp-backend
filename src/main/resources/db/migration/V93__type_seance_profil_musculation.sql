-- ============================================================
-- V93 — Profil des types de séance, couleur, et paramètres de musculation
--
-- PROBLÈME 1 — les 7 types de séance étaient posés HORS Flyway (dump initial) :
--   seul MATCH était garanti (V22). Rien n'assurait que la production ait le même
--   catalogue que le développement. C'est exactement le piège qui a produit les 404
--   des paramètres Performance, corrigé par la V71 sur la table `configuration`.
--   → on sème les 7 types en ON CONFLICT DO NOTHING (aucun écrasement).
--
-- PROBLÈME 2 — tout le code postulait « une séance = du déplacement mesuré au GPS » :
--   le formulaire réclamait une distance attendue pour une séance de musculation, et
--   la carte du calendrier annonçait « < 3 000 m total » sur la séance FORCE. Le type
--   ne déclarait pas sa NATURE. → colonne `profil` :
--     · TERRAIN             : distance, haute intensité, sprints, GPS (défaut)
--     · MUSCULATION         : tonnage, séries, RPE — JAMAIS de mètres
--     · SANS_CHARGE_EXTERNE : piscine, vidéo, récupération — RPE et durée seulement
--
-- PROBLÈME 3 — les couleurs des types étaient codées en dur dans le front : un type
--   créé par un club sortait gris-violet sans aucune information. → colonne `couleur`.
--
-- Le type FORCE (« Seance force ») est REQUALIFIÉ en musculation plutôt que doublé :
-- les séances déjà planifiées basculent ainsi dans le bon affichage, sans migration
-- de données ni doublon dans le menu déroulant.
-- ============================================================
SET search_path = public;

-- ── 1) Nature du type + couleur ────────────────────────────────────────────
ALTER TABLE type_seance ADD COLUMN profil  varchar(24) NOT NULL DEFAULT 'TERRAIN';
ALTER TABLE type_seance ADD COLUMN couleur varchar(9);

ALTER TABLE type_seance ADD CONSTRAINT type_seance_profil_chk
    CHECK (profil IN ('TERRAIN', 'MUSCULATION', 'SANS_CHARGE_EXTERNE'));

-- ── 2) Seed du catalogue (ON CONFLICT : ne touche jamais un type existant) ──
INSERT INTO type_seance (code, libelle, jour_semaine, intensite_theorique, objectif_principal, duree_theorique_min, profil, couleur) VALUES
  ('REPRISE',      'Reprise légère',            NULL, 1, 'recuperation', 60, 'TERRAIN',     '#22c55e'),
  ('TECHNIQUE',    'Séance technique',          NULL, 3, 'tactique',     90, 'TERRAIN',     '#0ea5a0'),
  ('INTENSIF',     'Séance intensive',          NULL, 4, 'endurance',    90, 'TERRAIN',     '#6366f1'),
  ('PRE_MATCH',    'Veille de match',           NULL, 2, 'activation',   60, 'TERRAIN',     '#eab308'),
  ('MATCH',        'Match officiel',            NULL, 5, 'compétition',  90, 'TERRAIN',     '#ef4444'),
  ('MATCH_AMICAL', 'Match amical',              NULL, 4, 'compétition',  90, 'TERRAIN',     '#f97316'),
  ('FORCE',        'Musculation / Renforcement', NULL, 4, 'force',       80, 'MUSCULATION', '#8b5cf6')
ON CONFLICT (code) DO NOTHING;

-- ── 3) Mise à niveau des types déjà présents (le seed ci-dessus les a ignorés) ──
-- Les couleurs reprennent EXACTEMENT celles qui étaient codées en dur dans le front,
-- pour que la refonte de la carte ne change pas l'apparence du calendrier.
UPDATE type_seance SET couleur = '#22c55e' WHERE code = 'REPRISE'      AND couleur IS NULL;
UPDATE type_seance SET couleur = '#0ea5a0' WHERE code = 'TECHNIQUE'    AND couleur IS NULL;
UPDATE type_seance SET couleur = '#6366f1' WHERE code = 'INTENSIF'     AND couleur IS NULL;
UPDATE type_seance SET couleur = '#eab308' WHERE code = 'PRE_MATCH'    AND couleur IS NULL;
UPDATE type_seance SET couleur = '#ef4444' WHERE code = 'MATCH'        AND couleur IS NULL;
UPDATE type_seance SET couleur = '#f97316' WHERE code = 'MATCH_AMICAL' AND couleur IS NULL;
UPDATE type_seance SET couleur = '#8b5cf6' WHERE code = 'FORCE'        AND couleur IS NULL;

-- Requalification de FORCE : le libellé parlait de « force » alors que le front le
-- décrivait déjà comme du renforcement musculaire… en kilomètres.
UPDATE type_seance
   SET profil = 'MUSCULATION',
       libelle = 'Musculation / Renforcement'
 WHERE code = 'FORCE';

-- ── 4) Paramètres propres à une séance de musculation (tous nullable) ───────
-- Portés par la séance, comme les objectifs de volume du profil TERRAIN.
-- Le tonnage et le %1RM sont volontairement ABSENTS : ils supposeraient de relever
-- les charges soulevées joueur par joueur, ce qui relève du carnet individuel (non fait).
ALTER TABLE seance ADD COLUMN muscu_qualite      varchar(30);
ALTER TABLE seance ADD COLUMN muscu_regime       varchar(20);
ALTER TABLE seance ADD COLUMN muscu_nb_series    smallint;
ALTER TABLE seance ADD COLUMN muscu_nb_repetitions integer;

ALTER TABLE seance ADD CONSTRAINT seance_muscu_qualite_chk
    CHECK (muscu_qualite IS NULL OR muscu_qualite IN (
        'FORCE_MAX', 'HYPERTROPHIE', 'PUISSANCE', 'ENDURANCE_FORCE', 'PREVENTION', 'REATHLETISATION'));

-- Le régime de contraction est le champ le plus utile du lot : une séance EXCENTRIQUE
-- déclenche des courbatures à J+2 que le moteur de fatigue interprète aujourd'hui comme
-- une dégradation anormale du ressenti.
ALTER TABLE seance ADD CONSTRAINT seance_muscu_regime_chk
    CHECK (muscu_regime IS NULL OR muscu_regime IN (
        'CONCENTRIQUE', 'EXCENTRIQUE', 'PLIOMETRIE', 'ISOMETRIE', 'MIXTE'));

ALTER TABLE seance ADD CONSTRAINT seance_muscu_series_chk
    CHECK (muscu_nb_series IS NULL OR muscu_nb_series BETWEEN 1 AND 200);
ALTER TABLE seance ADD CONSTRAINT seance_muscu_reps_chk
    CHECK (muscu_nb_repetitions IS NULL OR muscu_nb_repetitions BETWEEN 1 AND 5000);
