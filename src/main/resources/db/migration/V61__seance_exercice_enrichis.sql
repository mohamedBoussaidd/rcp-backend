-- ============================================================
-- V61 — Exercice & Séance enrichis (module `seance_avancee`)
--
-- Mode avancé optionnel inspiré des fiches pro (OL / Hauts Lyonnais) :
--  · exercice : cadre pédagogique + organisation (tout nullable — le mode
--    simplifié reste identique et suffisant, la densité m²/joueur est CALCULÉE) ;
--  · séance : durée effective, objectifs pédagogiques, dominantes et
--    sous-principes du projet de jeu (référentiels seedés), blocs (temps de
--    séance) au-dessus des lignes d'exercices, groupes du jour (équipes
--    couleur / groupes libres — les groupes Blessés/Réathlétisation/Présents
--    sont CALCULÉS à la volée, jamais stockés) ;
--  · preference_utilisateur : clé/valeur générique par compte (mode avancé,
--    puis style de rendu du chantier 2.5D).
--
-- Rétrocompatibilité stricte : seance_exercice.bloc_id NULL = liste plate
-- actuelle ; groupe_seance.bloc_id NULL = groupe valable toute la séance
-- (résolution : groupes du bloc s'il en existe, sinon groupes globaux).
-- Le badge J±X n'est jamais stocké (calculé depuis les séances de type MATCH*).
-- ============================================================
SET search_path = public;

-- ── Exercice : cadre pédagogique + organisation ──────────────────────────
ALTER TABLE exercice
    ADD COLUMN contexte_pedagogique   text,
    ADD COLUMN niveau_objectif        varchar(40)
        CHECK (niveau_objectif IN ('TEMPS_DE_JEU','PRINCIPE_ACTION','REGLE_ACTION_COLLECTIVE','REGLE_ACTION_INDIVIDUELLE','MOYEN')),
    ADD COLUMN echelle_effectif       varchar(20)
        CHECK (echelle_effectif IN ('COLLECTIF','INTERSECTORIEL','SECTORIEL','GROUPAL','INDIVIDUEL')),
    ADD COLUMN dominante_tactique_org  text,
    ADD COLUMN dominante_tactique_fonc text,
    ADD COLUMN dominante_mental        text,
    ADD COLUMN dominante_technique     text,
    ADD COLUMN dominante_athletique    text,
    ADD COLUMN but_systeme_marque      text,
    ADD COLUMN regles_jeu              text,
    ADD COLUMN variables_pedagogiques  text,
    ADD COLUMN reperes_perceptifs      text,
    ADD COLUMN comportements_attendus  text,
    ADD COLUMN terrain_longueur_m      numeric(5,1),
    ADD COLUMN terrain_largeur_m       numeric(5,1),
    ADD COLUMN format_joueurs          varchar(120),
    ADD COLUMN nb_joueurs_total        smallint,
    ADD COLUMN sequencage              varchar(120);

-- ── Séance : durée effective + objectifs pédagogiques ────────────────────
ALTER TABLE seance
    ADD COLUMN duree_effective_minutes smallint,
    ADD COLUMN obj_tactique_org        text,
    ADD COLUMN obj_tactique_fonc       text,
    ADD COLUMN obj_mental              text,
    ADD COLUMN obj_technique           text,
    ADD COLUMN obj_athletique          text;

-- ── Référentiels seedés (globaux ; paramétrage par club = backlog v2) ────
CREATE TABLE referentiel_dominante (
    id      uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    code    varchar(40) NOT NULL UNIQUE,
    libelle varchar(80) NOT NULL,
    famille varchar(12) NOT NULL CHECK (famille IN ('SEANCE','ATHLETIQUE')),
    ordre   smallint NOT NULL DEFAULT 0
);

INSERT INTO referentiel_dominante (code, libelle, famille, ordre) VALUES
 ('technique',       'Technique',        'SEANCE', 0),
 ('tactique',        'Tactique',         'SEANCE', 1),
 ('physique',        'Physique',         'SEANCE', 2),
 ('musculaire',      'Musculaire',       'SEANCE', 3),
 ('recuperation',    'Récupération',     'SEANCE', 4),
 ('mental',          'Mental',           'SEANCE', 5),
 ('cpa',             'CPA',              'SEANCE', 6),
 ('specifique',      'Spécifique',       'SEANCE', 7),
 ('recup_aerobie',   'Récup aérobie',    'ATHLETIQUE', 0),
 ('vivacite',        'Vivacité',         'ATHLETIQUE', 1),
 ('puissance_aerobie','Puissance aérobie','ATHLETIQUE', 2),
 ('vitesse',         'Vitesse',          'ATHLETIQUE', 3),
 ('force_specifique','Force spécifique', 'ATHLETIQUE', 4);

-- Sous-principes du projet de jeu. `phase` reprend les PhaseKey du moteur
-- tactique (OFF, DEF, T_OD, T_DO) + CPA_OFF/CPA_DEF qui n'existent que comme
-- référentiel de séance (les CPA ne sont pas des phases du moteur).
CREATE TABLE referentiel_sous_principe (
    id      uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    code    varchar(40) NOT NULL UNIQUE,
    libelle varchar(80) NOT NULL,
    phase   varchar(10) NOT NULL CHECK (phase IN ('OFF','DEF','T_OD','T_DO','CPA_OFF','CPA_DEF')),
    ordre   smallint NOT NULL DEFAULT 0
);

INSERT INTO referentiel_sous_principe (code, libelle, phase, ordre) VALUES
 ('sortie_de_balle',   'Sortie de balle',            'OFF',  0),
 ('conservation',      'Conservation',               'OFF',  1),
 ('progression',       'Progression',                'OFF',  2),
 ('desequilibre',      'Déséquilibre',               'OFF',  3),
 ('finition',          'Finition',                   'OFF',  4),
 ('defendre_en_zone',  'Défendre en zone',           'DEF',  0),
 ('pressing',          'Pressing',                   'DEF',  1),
 ('bloc',              'Bloc équipe',                'DEF',  2),
 ('proteger_le_but',   'Protéger le but',            'DEF',  3),
 ('contre_attaque',    'Contre-attaque',             'T_OD', 0),
 ('conservation_post_recup', 'Conservation après récupération', 'T_OD', 1),
 ('contre_pressing',   'Contre-pressing',            'T_DO', 0),
 ('repli_defensif',    'Repli défensif',             'T_DO', 1),
 ('cpa_offensifs',     'CPA offensifs',              'CPA_OFF', 0),
 ('cpa_defensifs',     'CPA défensifs',              'CPA_DEF', 0);

CREATE TABLE seance_dominante (
    seance_id    uuid NOT NULL REFERENCES seance(id) ON DELETE CASCADE,
    dominante_id uuid NOT NULL REFERENCES referentiel_dominante(id) ON DELETE CASCADE,
    PRIMARY KEY (seance_id, dominante_id)
);

CREATE TABLE seance_sous_principe (
    seance_id         uuid NOT NULL REFERENCES seance(id) ON DELETE CASCADE,
    sous_principe_id  uuid NOT NULL REFERENCES referentiel_sous_principe(id) ON DELETE CASCADE,
    PRIMARY KEY (seance_id, sous_principe_id)
);

-- ── Blocs (temps de séance) ──────────────────────────────────────────────
CREATE TABLE bloc_seance (
    id            uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    seance_id     uuid NOT NULL REFERENCES seance(id) ON DELETE CASCADE,
    ordre         smallint NOT NULL DEFAULT 0,
    libelle       varchar(120) NOT NULL,
    sequencage    varchar(120),
    duree_minutes smallint,
    zone_terrain  varchar(120)
);

CREATE INDEX idx_bloc_seance_seance ON bloc_seance(seance_id);

-- Staff affecté à un bloc : comptes staff du club (kiné, prépa, adjoint…).
CREATE TABLE bloc_seance_staff (
    bloc_id        uuid NOT NULL REFERENCES bloc_seance(id) ON DELETE CASCADE,
    utilisateur_id uuid NOT NULL REFERENCES utilisateur(id) ON DELETE CASCADE,
    PRIMARY KEY (bloc_id, utilisateur_id)
);

-- Rattachement optionnel des lignes d'exercices à un bloc (NULL = liste plate).
ALTER TABLE seance_exercice
    ADD COLUMN bloc_id uuid REFERENCES bloc_seance(id) ON DELETE SET NULL;

-- ── Groupes du jour (équipes couleur / groupes libres) ───────────────────
CREATE TABLE groupe_seance (
    id        uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    seance_id uuid NOT NULL REFERENCES seance(id) ON DELETE CASCADE,
    bloc_id   uuid REFERENCES bloc_seance(id) ON DELETE CASCADE,
    type      varchar(12) NOT NULL DEFAULT 'LIBRE' CHECK (type IN ('COULEUR','LIBRE')),
    libelle   varchar(80) NOT NULL,
    couleur   varchar(20),
    ordre     smallint NOT NULL DEFAULT 0
);

CREATE INDEX idx_groupe_seance_seance ON groupe_seance(seance_id);

CREATE TABLE groupe_seance_joueur (
    groupe_id uuid NOT NULL REFERENCES groupe_seance(id) ON DELETE CASCADE,
    joueur_id uuid NOT NULL REFERENCES joueur(id) ON DELETE CASCADE,
    PRIMARY KEY (groupe_id, joueur_id)
);

-- ── Préférences utilisateur (clé/valeur générique) ───────────────────────
CREATE TABLE preference_utilisateur (
    utilisateur_id uuid NOT NULL REFERENCES utilisateur(id) ON DELETE CASCADE,
    cle            varchar(60) NOT NULL,
    valeur         text NOT NULL,
    updated_at     timestamp NOT NULL DEFAULT now(),
    PRIMARY KEY (utilisateur_id, cle)
);

-- ── Module & permission ──────────────────────────────────────────────────
-- Module `seance_avancee` : pack Complet uniquement (add-on ailleurs via club_module).
INSERT INTO pack_module (pack_code, module_code) VALUES
 ('complet', 'seance_avancee')
ON CONFLICT DO NOTHING;

-- Écriture des enrichissements gated par `seance_avancee:access` (lecture libre :
-- un club qui perd l'add-on garde ses données visibles). Distribution : rôles
-- qui préparent les séances — Président, Entraîneur, Préparateur, Entraîneur en chef.
INSERT INTO role_permission (role_id, permission) VALUES
 ('a0000000-0000-0000-0000-000000000001', 'seance_avancee:access'),
 ('a0000000-0000-0000-0000-000000000002', 'seance_avancee:access'),
 ('a0000000-0000-0000-0000-000000000003', 'seance_avancee:access'),
 ('a0000000-0000-0000-0000-000000000006', 'seance_avancee:access')
ON CONFLICT (role_id, permission) DO NOTHING;
