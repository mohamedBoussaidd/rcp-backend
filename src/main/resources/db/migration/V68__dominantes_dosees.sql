-- V68 — Les dominantes se DOSENT au lieu de se raconter.
--
-- V61 avait posé les 5 axes (tactique organisationnelle, tactique fonctionnelle, mental,
-- technique, athlétique) à deux endroits, sous deux formes qui ne se parlaient pas :
--   · sur l'exercice, cinq champs TEXTE (`dominante_*`) — un mur de cinq zones de saisie
--     identiques que personne ne remplit en entier ;
--   · sur la séance, cinq champs TEXTE d'objectifs pédagogiques (`obj_*`) portant exactement
--     les mêmes cinq libellés.
-- On ne pouvait donc ni comparer deux exercices, ni agréger les dominantes d'une séance : du
-- texte libre ne s'additionne pas.
--
-- Cette migration ajoute l'INTENSITÉ 0-5 par axe, aux trois endroits (exercice, séance,
-- modèle de séance). Le texte existant n'est pas touché : il devient la NOTE facultative qui
-- précise l'axe, sous la jauge. Aucune donnée n'est perdue ni réinterprétée.
--
-- Ce qui n'est volontairement PAS touché : `referentiel_dominante` et ses tables de liaison
-- (seance_dominante, seance_modele_dominante). Ce référentiel de 13 tags en deux familles
-- (SEANCE : technique/tactique/physique/musculaire/récupération/mental/CPA/spécifique ;
-- ATHLETIQUE : récup aérobie/vivacité/puissance aérobie/vitesse/force spécifique) décrit la
-- NATURE de la séance, pas le dosage de ses cinq axes pédagogiques. La famille ATHLETIQUE en
-- particulier n'a aucun équivalent dans les cinq axes. Les deux notions coexistent — elles
-- sont simplement renommées à l'écran pour qu'on cesse de les confondre.
SET search_path = public;

-- ── Exercice : intensité à côté du texte, qui devient la note de l'axe ───
ALTER TABLE exercice
    ADD COLUMN dominante_tactique_org_intensite  smallint
        CHECK (dominante_tactique_org_intensite  BETWEEN 0 AND 5),
    ADD COLUMN dominante_tactique_fonc_intensite smallint
        CHECK (dominante_tactique_fonc_intensite BETWEEN 0 AND 5),
    ADD COLUMN dominante_mental_intensite        smallint
        CHECK (dominante_mental_intensite        BETWEEN 0 AND 5),
    ADD COLUMN dominante_technique_intensite     smallint
        CHECK (dominante_technique_intensite     BETWEEN 0 AND 5),
    ADD COLUMN dominante_athletique_intensite    smallint
        CHECK (dominante_athletique_intensite    BETWEEN 0 AND 5);

COMMENT ON COLUMN exercice.dominante_tactique_org IS
    'Note libre précisant l''axe. Depuis V68 le dosage se lit sur '
    '`dominante_tactique_org_intensite` — ce texte ne le remplace plus, il le commente.';

-- Un exercice déjà décrit sur un axe travaille forcément cet axe : sans reprise, toutes les
-- fiches existantes retomberaient à zéro partout et l'écran mentirait dès le premier
-- affichage. On part de 3/5 (« présent, sans plus »), volontairement médian : c'est une
-- valeur que le coach corrige d'un clic, pas une mesure qu'on prétend connaître.
UPDATE exercice SET dominante_tactique_org_intensite  = 3 WHERE nullif(btrim(dominante_tactique_org),  '') IS NOT NULL;
UPDATE exercice SET dominante_tactique_fonc_intensite = 3 WHERE nullif(btrim(dominante_tactique_fonc), '') IS NOT NULL;
UPDATE exercice SET dominante_mental_intensite        = 3 WHERE nullif(btrim(dominante_mental),        '') IS NOT NULL;
UPDATE exercice SET dominante_technique_intensite     = 3 WHERE nullif(btrim(dominante_technique),     '') IS NOT NULL;
UPDATE exercice SET dominante_athletique_intensite    = 3 WHERE nullif(btrim(dominante_athletique),    '') IS NOT NULL;

-- ── Séance : mêmes cinq axes, mêmes noms de colonnes qu'`exercice` ───────
-- Les `obj_*` de V61 restent la ligne de détail affichée sous la jauge.
ALTER TABLE seance
    ADD COLUMN dominante_tactique_org_intensite  smallint
        CHECK (dominante_tactique_org_intensite  BETWEEN 0 AND 5),
    ADD COLUMN dominante_tactique_fonc_intensite smallint
        CHECK (dominante_tactique_fonc_intensite BETWEEN 0 AND 5),
    ADD COLUMN dominante_mental_intensite        smallint
        CHECK (dominante_mental_intensite        BETWEEN 0 AND 5),
    ADD COLUMN dominante_technique_intensite     smallint
        CHECK (dominante_technique_intensite     BETWEEN 0 AND 5),
    ADD COLUMN dominante_athletique_intensite    smallint
        CHECK (dominante_athletique_intensite    BETWEEN 0 AND 5);

UPDATE seance SET dominante_tactique_org_intensite  = 3 WHERE nullif(btrim(obj_tactique_org),  '') IS NOT NULL;
UPDATE seance SET dominante_tactique_fonc_intensite = 3 WHERE nullif(btrim(obj_tactique_fonc), '') IS NOT NULL;
UPDATE seance SET dominante_mental_intensite        = 3 WHERE nullif(btrim(obj_mental),        '') IS NOT NULL;
UPDATE seance SET dominante_technique_intensite     = 3 WHERE nullif(btrim(obj_technique),     '') IS NOT NULL;
UPDATE seance SET dominante_athletique_intensite    = 3 WHERE nullif(btrim(obj_athletique),    '') IS NOT NULL;

-- ── Modèle de séance : sans quoi `planifier()` reperdrait le dosage ──────
-- Même raison qu'en V63 : un gabarit qui ne recopie pas ce que la séance sait porter fait
-- retomber le coach au niveau zéro dès qu'il passe par la bibliothèque.
ALTER TABLE seance_modele
    ADD COLUMN dominante_tactique_org_intensite  smallint
        CHECK (dominante_tactique_org_intensite  BETWEEN 0 AND 5),
    ADD COLUMN dominante_tactique_fonc_intensite smallint
        CHECK (dominante_tactique_fonc_intensite BETWEEN 0 AND 5),
    ADD COLUMN dominante_mental_intensite        smallint
        CHECK (dominante_mental_intensite        BETWEEN 0 AND 5),
    ADD COLUMN dominante_technique_intensite     smallint
        CHECK (dominante_technique_intensite     BETWEEN 0 AND 5),
    ADD COLUMN dominante_athletique_intensite    smallint
        CHECK (dominante_athletique_intensite    BETWEEN 0 AND 5);

UPDATE seance_modele SET dominante_tactique_org_intensite  = 3 WHERE nullif(btrim(obj_tactique_org),  '') IS NOT NULL;
UPDATE seance_modele SET dominante_tactique_fonc_intensite = 3 WHERE nullif(btrim(obj_tactique_fonc), '') IS NOT NULL;
UPDATE seance_modele SET dominante_mental_intensite        = 3 WHERE nullif(btrim(obj_mental),        '') IS NOT NULL;
UPDATE seance_modele SET dominante_technique_intensite     = 3 WHERE nullif(btrim(obj_technique),     '') IS NOT NULL;
UPDATE seance_modele SET dominante_athletique_intensite    = 3 WHERE nullif(btrim(obj_athletique),    '') IS NOT NULL;
