-- V66 — Le bloc de séance : type, zones du terrain, rôles du staff.
--
-- Trois manques traités ensemble parce qu'ils portent tous sur le même objet :
--
--   1. Le bloc n'avait pas de TYPE, alors que « échauffement / situation / jeu / retour au calme »
--      est le premier tri mental d'un coach.
--   2. La zone du terrain était un TEXTE LIBRE (« demi-terrain gauche »). Non comparable, donc
--      impossible de détecter que deux blocs simultanés occupent le même espace. Elle devient
--      un ensemble de zones parmi 8 — c'est ce qui rend l'alerte de conflit possible, et c'est la
--      vraie raison de structurer, la carte n'en est que l'affichage.
--   3. Le staff était affecté au bloc sans dire QUOI il y fait. On ajoute des rôles.
--
-- Découpage retenu : 4 bandes en longueur × 2 moitiés (« demi-terrain gauche » = zones 1-2-3-4).
--
--        moitié A     moitié B
--       ┌─────────┬─────────┐
--       │    1    │    5    │   couloir gauche
--       ├─────────┼─────────┤
--  BUT  │    2    │    6    │   demi-espace gauche   BUT
--       ├─────────┼─────────┤
--       │    3    │    7    │   demi-espace droit
--       ├─────────┼─────────┤
--       │    4    │    8    │   couloir droit
--       └─────────┴─────────┘

-- ── 1. Type de bloc ──────────────────────────────────────────────────────
ALTER TABLE bloc_seance ADD COLUMN type varchar(20);

ALTER TABLE bloc_seance ADD CONSTRAINT bloc_seance_type_chk
    CHECK (type IS NULL OR type IN ('ECHAUFFEMENT', 'SITUATION', 'JEU', 'RETOUR_AU_CALME'));

COMMENT ON COLUMN bloc_seance.type IS
    'Moment de séance. Récupère la valeur « echauffement » qui traînait dans les catégories '
    'd''exercice, où elle n''était pas une catégorie mais un moment.';

-- ── 2. Zones du terrain (1..8) ───────────────────────────────────────────
CREATE TABLE bloc_seance_zone (
    bloc_id uuid     NOT NULL REFERENCES bloc_seance(id) ON DELETE CASCADE,
    zone    smallint NOT NULL CHECK (zone BETWEEN 1 AND 8),
    PRIMARY KEY (bloc_id, zone)
);

COMMENT ON TABLE bloc_seance_zone IS
    'Zones occupées par un bloc. Plusieurs lignes par bloc : un demi-terrain = 4 zones, '
    'un jeu réduit = 1 seule.';

-- Les textes libres existants ne sont PAS convertis : « près des cages », « côté vestiaire »
-- n'ont pas d'équivalent fiable parmi 8 zones, et deviner à la place du coach produirait une
-- carte fausse — plus nuisible qu'une carte vide. On conserve donc le texte dans les notes de
-- la séance pour qu'il puisse recocher en connaissance de cause, puis on supprime la colonne.
UPDATE seance s
SET    description = concat_ws(' · ', nullif(btrim(s.description), ''),
                               'Zones (à recocher sur la carte) : ' || z.textes)
FROM  (SELECT seance_id, string_agg(libelle || ' = ' || zone_terrain, ' ; ' ORDER BY ordre) AS textes
       FROM   bloc_seance
       WHERE  nullif(btrim(zone_terrain), '') IS NOT NULL
       GROUP  BY seance_id) z
WHERE  s.id = z.seance_id;

ALTER TABLE bloc_seance DROP COLUMN zone_terrain;

-- Même traitement sur le bloc de MODÈLE, sans quoi `planifier()` reperdrait le type et les
-- zones — exactement le bug corrigé au lot 4b (V63), qu'on ne réintroduit pas ici.
-- Les RÔLES, eux, restent propres à la séance : ils désignent qui fait quoi un jour donné.
ALTER TABLE bloc_seance_modele ADD COLUMN type varchar(20);

ALTER TABLE bloc_seance_modele ADD CONSTRAINT bloc_seance_modele_type_chk
    CHECK (type IS NULL OR type IN ('ECHAUFFEMENT', 'SITUATION', 'JEU', 'RETOUR_AU_CALME'));

CREATE TABLE bloc_seance_modele_zone (
    bloc_id uuid     NOT NULL REFERENCES bloc_seance_modele(id) ON DELETE CASCADE,
    zone    smallint NOT NULL CHECK (zone BETWEEN 1 AND 8),
    PRIMARY KEY (bloc_id, zone)
);

ALTER TABLE bloc_seance_modele DROP COLUMN zone_terrain;

-- ── 3. Rôles du staff sur un bloc ────────────────────────────────────────
-- Référentiel FIGÉ (posé par le super-admin, commun à tous les clubs) : même patron que
-- referentiel_dominante, et la comparabilité entre clubs reste possible.
CREATE TABLE referentiel_role_bloc (
    code    varchar(20) PRIMARY KEY,
    libelle varchar(60) NOT NULL,
    icone   varchar(8)  NOT NULL,
    ordre   smallint    NOT NULL DEFAULT 0
);

INSERT INTO referentiel_role_bloc (code, libelle, icone, ordre) VALUES
 ('MENEUR',      'Mène le bloc',      '▶', 0),
 ('ARBITRE',     'Arbitre',           '⚖', 1),
 ('BALLONS',     'Source de balle',   '⚽', 2),
 ('CHRONO',      'Chrono',            '⏱', 3),
 ('OBSERVATION', 'Observation',       '👁', 4),
 ('SOINS',       'Soins / sécurité',  '🩺', 5);

-- Table SÉPARÉE de l'affectation, volontairement : `bloc_seance_staff` continue de dire « cette
-- personne est sur ce bloc », les rôles sont des étiquettes posées par-dessus (0 à n par
-- personne — celui qui mène arbitre souvent aussi). Cela évite surtout d'inventer un rôle pour
-- les affectations déjà en base : elles restent simplement « présent, rôle non précisé ».
CREATE TABLE bloc_seance_staff_role (
    bloc_id        uuid        NOT NULL,
    utilisateur_id uuid        NOT NULL,
    role           varchar(20) NOT NULL REFERENCES referentiel_role_bloc(code),
    PRIMARY KEY (bloc_id, utilisateur_id, role),
    FOREIGN KEY (bloc_id, utilisateur_id)
        REFERENCES bloc_seance_staff(bloc_id, utilisateur_id) ON DELETE CASCADE
);

-- « Un seul MENEUR par bloc » : deux personnes qui mènent le même atelier, c'est une consigne
-- contradictoire sur le terrain. La règle est portée par la base, pas seulement par l'écran.
CREATE UNIQUE INDEX idx_bloc_meneur_unique
    ON bloc_seance_staff_role(bloc_id) WHERE role = 'MENEUR';
