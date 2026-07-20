-- V67 — Deux corrections décidées après coup sur le nettoyage V65.
--
-- V65 était déjà appliquée quand ces deux arbitrages ont été rendus ; une migration jouée ne se
-- réécrit pas (Flyway refuserait de démarrer sur un checksum différent), d'où cette migration
-- corrective plutôt qu'une retouche de V65.
--
--   1. Le responsable : V65 avait conservé l'ancien nom dans une colonne legacy en lecture seule.
--      Arbitrage retenu : la colonne disparaît vraiment, le texte part dans les notes.
--   2. La catégorie d'exercice : V65 ne l'avait pas traitée. Elle est ici éclatée en deux axes.

-- ════════════ 1. Le responsable : plus de colonne legacy ════════════

UPDATE seance
SET    description = concat_ws(' · ', nullif(btrim(description), ''),
                               'Responsable : ' || btrim(responsable_texte))
WHERE  nullif(btrim(responsable_texte), '') IS NOT NULL
  AND  responsable_id IS NULL;

ALTER TABLE seance DROP COLUMN responsable_texte;

-- ════════════ 2. categorie → forme de travail + thèmes de jeu ════════════
--
-- Une seule liste mélangeait TROIS choses de nature différente : un moment de séance
-- (echauffement), des formes de travail (jeu_reduit, match_a_theme) et des thèmes de jeu
-- (conservation, finition, transition, coup_pied_arrete). Ces derniers faisaient en plus doublon
-- avec le référentiel des sous-principes déjà utilisé par la séance — deux vocabulaires
-- concurrents pour classer la même chose. On sépare les deux axes, et les thèmes rejoignent le
-- référentiel commun.

ALTER TABLE exercice ADD COLUMN forme varchar(20);

ALTER TABLE exercice ADD CONSTRAINT exercice_forme_chk
    CHECK (forme IS NULL OR forme IN
           ('ECHAUFFEMENT', 'ANALYTIQUE', 'SITUATION', 'JEU_REDUIT', 'MATCH_A_THEME'));

COMMENT ON COLUMN exercice.forme IS
    'Forme de travail. « echauffement » n''était pas une catégorie d''exercice mais un moment '
    'de séance : il est aussi devenu un type de bloc (V66).';

CREATE TABLE exercice_sous_principe (
    exercice_id      uuid NOT NULL REFERENCES exercice(id) ON DELETE CASCADE,
    sous_principe_id uuid NOT NULL REFERENCES referentiel_sous_principe(id) ON DELETE CASCADE,
    PRIMARY KEY (exercice_id, sous_principe_id)
);

COMMENT ON TABLE exercice_sous_principe IS
    'Thèmes de jeu de l''exercice, pris dans le MÊME référentiel que la séance — un seul '
    'vocabulaire dans toute l''application.';

-- Axe « forme de travail ».
UPDATE exercice SET forme = CASE categorie
    WHEN 'echauffement'   THEN 'ECHAUFFEMENT'
    WHEN 'technique'      THEN 'ANALYTIQUE'
    WHEN 'tactique'       THEN 'SITUATION'
    WHEN 'jeu_reduit'     THEN 'JEU_REDUIT'
    WHEN 'match_a_theme'  THEN 'MATCH_A_THEME'
    END
WHERE categorie IS NOT NULL;

-- Axe « thème de jeu » : les 4 catégories qui doublonnaient le référentiel y sont reversées.
-- `transition` → contre-attaque (transition offensive) : c'est le sens qu'on lui donne en séance.
INSERT INTO exercice_sous_principe (exercice_id, sous_principe_id)
SELECT e.id, sp.id
FROM   exercice e
JOIN   referentiel_sous_principe sp ON sp.code = CASE e.categorie
           WHEN 'conservation'      THEN 'conservation'
           WHEN 'finition'          THEN 'finition'
           WHEN 'transition'        THEN 'contre_attaque'
           WHEN 'coup_pied_arrete'  THEN 'cpa_offensifs'
           END
ON CONFLICT DO NOTHING;

ALTER TABLE exercice DROP COLUMN categorie;
