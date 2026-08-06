-- ============================================================
-- V102 — Modèles d'objectif et objectifs de période (lot 2)
--
-- Un modèle porte la FORME d'une période, le référentiel (V101) porte l'ÉCHELLE. Le modèle ne
-- stocke donc AUCUN kilomètre : uniquement des phases et des pourcentages de la cible du
-- référentiel. C'est ce qui permet au même « Prépa — progression classique » de servir un club
-- N1 (33 000 m de référence hebdo) et un club régional (29 000 m), chacun obtenant ses propres
-- valeurs sans qu'on duplique quoi que ce soit.
--
-- POURQUOI DES PHASES ET PAS DES SEMAINES. Stocker six semaines fixes puis les interpoler sur
-- neuf détruit deux choses à la fois : le pic est raboté (moyenné avec ses voisines, la valeur
-- maximale n'est plus jamais atteinte) et la décharge s'étale sur deux semaines, donc ce n'est
-- plus une chute mais un ralentissement — le joueur arrive au premier match sans avoir été
-- déchargé, et rien ne le signale. Avec des phases, chaque bloc a ses propres semaines : on
-- interpole À L'INTÉRIEUR d'une phase, jamais entre deux.
--
-- Le pourcentage est une décision de STOCKAGE, pas d'affichage : l'éditeur montre des mètres.
-- ============================================================
SET search_path = public;

-- ── Modèle réutilisable du club ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS modele_objectif (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id       uuid NOT NULL REFERENCES club (id) ON DELETE CASCADE,
    nom           varchar(160) NOT NULL,
    type_periode  varchar(20)  NOT NULL,
    cree_par      uuid,
    created_at    timestamp NOT NULL DEFAULT now(),
    updated_at    timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_modele_objectif_club ON modele_objectif (club_id);

-- ── Phases d'un modèle ──────────────────────────────────────────────────────
-- `poids_duree` est une PART RELATIVE, jamais un nombre de semaines : des poids 2/2/1/1 donnent
-- 2/2/1/1 sur six semaines (le document de référence au chiffre près), 3/3/2/1 sur neuf, et
-- 1/1/1 sur trois — auquel cas l'application ANNONCE la phase supprimée au lieu de bricoler.
CREATE TABLE IF NOT EXISTS modele_objectif_phase (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    modele_id    uuid NOT NULL REFERENCES modele_objectif (id) ON DELETE CASCADE,
    ordre        smallint NOT NULL DEFAULT 0,
    nom          varchar(80) NOT NULL,
    poids_duree  smallint NOT NULL DEFAULT 1,
    CONSTRAINT modele_objectif_phase_poids_chk CHECK (poids_duree >= 1)
);

CREATE INDEX IF NOT EXISTS idx_modele_phase_modele ON modele_objectif_phase (modele_id);

-- ── Niveau d'une phase, PAR MÉTRIQUE ────────────────────────────────────────
-- Un pourcentage par métrique, jamais un coefficient global : sur le document de référence, le
-- volume monte de 67 % à 109 % de la charge de championnat pendant que la haute intensité part
-- de 45 % et culmine à 116 %. On ne réintroduit pas la vitesse au rythme du volume.
CREATE TABLE IF NOT EXISTS modele_objectif_phase_valeur (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    phase_id   uuid NOT NULL REFERENCES modele_objectif_phase (id) ON DELETE CASCADE,
    metrique   varchar(40) NOT NULL,
    pct_debut  integer NOT NULL,
    pct_fin    integer NOT NULL,
    priorite   varchar(20) NOT NULL DEFAULT 'IMPORTANT',
    CONSTRAINT modele_phase_valeur_priorite_chk
        CHECK (priorite IN ('SECONDAIRE', 'IMPORTANT', 'INTOUCHABLE')),
    CONSTRAINT modele_phase_valeur_unique UNIQUE (phase_id, metrique)
);

-- ── Instance : un modèle posé sur une période ───────────────────────────────
-- Une seule par période. Les valeurs générées sont FIGÉES et éditables case par case : corriger
-- le modèle ensuite ne rattrape pas les instances — un objectif déjà annoncé au groupe ne doit
-- pas bouger dans le dos du préparateur.
CREATE TABLE IF NOT EXISTS objectif_periode (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id         uuid NOT NULL REFERENCES club (id) ON DELETE CASCADE,
    periode_id      uuid NOT NULL REFERENCES periode_saison (id) ON DELETE CASCADE,
    modele_id       uuid REFERENCES modele_objectif (id) ON DELETE SET NULL,
    referentiel_id  uuid REFERENCES referentiel_objectif (id) ON DELETE SET NULL,
    phases_resume   text,
    avertissement   text,
    cree_par        uuid,
    created_at      timestamp NOT NULL DEFAULT now(),
    updated_at      timestamp NOT NULL DEFAULT now(),
    CONSTRAINT objectif_periode_unique UNIQUE (periode_id)
);

CREATE INDEX IF NOT EXISTS idx_objectif_periode_club ON objectif_periode (club_id);

-- ── Valeurs de l'instance, en mètres cette fois ─────────────────────────────
-- Une seule table pour les deux formes, distinguées par la colonne renseignée :
--   no_semaine rempli → trajectoire de préparation (une ligne par semaine, au niveau équipe)
--   poste rempli      → cibles de compétition (une fourchette par poste, toute la période)
-- `modifie_manuellement` permet de teinter les cases retouchées et d'avertir avant une
-- régénération, qui écraserait sinon le travail du préparateur en silence.
CREATE TABLE IF NOT EXISTS objectif_periode_valeur (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    objectif_periode_id   uuid NOT NULL REFERENCES objectif_periode (id) ON DELETE CASCADE,
    no_semaine            smallint,
    date_lundi            date,
    poste                 varchar(40),
    metrique              varchar(40) NOT NULL,
    valeur_min            integer,
    valeur_max            integer,
    priorite              varchar(20) NOT NULL DEFAULT 'IMPORTANT',
    phase_nom             varchar(80),
    modifie_manuellement  boolean NOT NULL DEFAULT false,
    CONSTRAINT objectif_periode_valeur_priorite_chk
        CHECK (priorite IN ('SECONDAIRE', 'IMPORTANT', 'INTOUCHABLE'))
);

CREATE INDEX IF NOT EXISTS idx_objectif_valeur_objectif
    ON objectif_periode_valeur (objectif_periode_id);

-- ============================================================
-- SEED — deux modèles pour CHAQUE club existant.
--
-- Les modèles appartiennent à un club (ils sont personnalisables), donc on les crée pour tous
-- les clubs déjà en base. Un club créé après cette migration n'en aura pas : il les crée depuis
-- l'écran, ou on les ajoutera au seeder de club.
--
-- « Prépa — progression classique » reproduit le document de référence : sur six semaines, les
-- poids 2/2/1/1 redonnent 67-79-91-100-109-91 % de la cible de championnat, soit exactement
-- 22/26/30/33/36/30 km pour une référence à 33 000 m.
-- ============================================================
INSERT INTO modele_objectif (id, club_id, nom, type_periode)
SELECT md5(c.id::text || 'prepa-classique')::uuid, c.id,
       'Prépa — progression classique', 'PREPARATION'
FROM club c
ON CONFLICT DO NOTHING;

INSERT INTO modele_objectif (id, club_id, nom, type_periode)
SELECT md5(c.id::text || 'championnat')::uuid, c.id,
       'Championnat — régime de croisière', 'COMPETITION'
FROM club c
ON CONFLICT DO NOTHING;

-- Phases de la préparation : les poids donnent la durée, les bornes donnent la courbe.
INSERT INTO modele_objectif_phase (id, modele_id, ordre, nom, poids_duree)
SELECT md5(m.id::text || p.nom)::uuid, m.id, p.ordre, p.nom, p.poids
FROM modele_objectif m
CROSS JOIN (VALUES
    (0::smallint, 'Accumulation',  2::smallint),
    (1::smallint, 'Développement', 2::smallint),
    (2::smallint, 'Pic',           1::smallint),
    (3::smallint, 'Décharge',      1::smallint)
) AS p(ordre, nom, poids)
WHERE m.type_periode = 'PREPARATION'
ON CONFLICT DO NOTHING;

INSERT INTO modele_objectif_phase (id, modele_id, ordre, nom, poids_duree)
SELECT md5(m.id::text || 'Championnat')::uuid, m.id, 0, 'Championnat', 1
FROM modele_objectif m
WHERE m.type_periode = 'COMPETITION'
ON CONFLICT DO NOTHING;

-- Niveaux par métrique. Le volume et la haute intensité ne suivent PAS la même courbe : la
-- vitesse part de plus bas (45 % contre 67 %) et monte plus haut (116 % contre 109 %).
-- Priorités : le volume est la monnaie d'échange, l'exposition V-max ne se sacrifie jamais.
INSERT INTO modele_objectif_phase_valeur (phase_id, metrique, pct_debut, pct_fin, priorite)
SELECT ph.id, v.metrique, v.pct_debut, v.pct_fin, v.priorite
FROM modele_objectif_phase ph
JOIN modele_objectif m ON m.id = ph.modele_id AND m.type_periode = 'PREPARATION'
JOIN (VALUES
    -- phase           métrique          début  fin   priorité
    ('Accumulation',  'distance_totale',   67,   79, 'SECONDAIRE'),
    ('Accumulation',  'distance_15',       60,   72, 'SECONDAIRE'),
    ('Accumulation',  'distance_19',       45,   58, 'IMPORTANT'),
    ('Accumulation',  'distance_24_28',    40,   55, 'IMPORTANT'),
    ('Accumulation',  'distance_28',       35,   50, 'IMPORTANT'),
    ('Accumulation',  'expo_vmax',         85,   88, 'INTOUCHABLE'),

    ('Développement', 'distance_totale',   91,  100, 'SECONDAIRE'),
    ('Développement', 'distance_15',       84,   95, 'SECONDAIRE'),
    ('Développement', 'distance_19',       77,   97, 'IMPORTANT'),
    ('Développement', 'distance_24_28',    72,   95, 'IMPORTANT'),
    ('Développement', 'distance_28',       68,   92, 'IMPORTANT'),
    ('Développement', 'expo_vmax',         90,   92, 'INTOUCHABLE'),

    ('Pic',           'distance_totale',  109,  109, 'SECONDAIRE'),
    ('Pic',           'distance_15',      112,  112, 'IMPORTANT'),
    ('Pic',           'distance_19',      116,  116, 'IMPORTANT'),
    ('Pic',           'distance_24_28',   115,  115, 'IMPORTANT'),
    ('Pic',           'distance_28',      112,  112, 'IMPORTANT'),
    ('Pic',           'expo_vmax',         94,   94, 'INTOUCHABLE'),

    -- La décharge fait chuter le volume et garde la vitesse : c'est tout l'enjeu d'un affûtage.
    ('Décharge',      'distance_totale',   91,   91, 'SECONDAIRE'),
    ('Décharge',      'distance_15',       86,   86, 'SECONDAIRE'),
    ('Décharge',      'distance_19',       84,   84, 'IMPORTANT'),
    ('Décharge',      'distance_24_28',    85,   85, 'IMPORTANT'),
    ('Décharge',      'distance_28',       82,   82, 'IMPORTANT'),
    ('Décharge',      'expo_vmax',         92,   92, 'INTOUCHABLE')
) AS v(phase, metrique, pct_debut, pct_fin, priorite) ON v.phase = ph.nom
ON CONFLICT ON CONSTRAINT modele_phase_valeur_unique DO NOTHING;

-- Championnat : le régime nominal du référentiel, sans montée.
INSERT INTO modele_objectif_phase_valeur (phase_id, metrique, pct_debut, pct_fin, priorite)
SELECT ph.id, v.metrique, 100, 100, v.priorite
FROM modele_objectif_phase ph
JOIN modele_objectif m ON m.id = ph.modele_id AND m.type_periode = 'COMPETITION'
JOIN (VALUES
    ('distance_totale', 'SECONDAIRE'),
    ('distance_15',     'SECONDAIRE'),
    ('distance_19',     'IMPORTANT'),
    ('distance_24_28',  'IMPORTANT'),
    ('distance_28',     'IMPORTANT'),
    ('expo_vmax',       'INTOUCHABLE')
) AS v(metrique, priorite) ON true
ON CONFLICT ON CONSTRAINT modele_phase_valeur_unique DO NOTHING;
