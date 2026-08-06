-- ============================================================
-- V101 — Référentiels de charge (lot 1 des « Objectifs de performance »)
--
-- Le référentiel répond à UNE question : « pour un joueur de ce poste, à ce niveau, qu'est-ce qui
-- est normal ? ». C'est une NORME fournie, pas un objectif décidé — l'objectif (ce que CE club
-- veut sur CETTE période) est un autre objet, qui arrive au lot 2.
--
-- Il a de la valeur TOUT SEUL : afficher « Habituel 24 km / Attendu N1 31–36 km » à côté d'un
-- joueur est déjà ce qui manquait le plus. Jusqu'ici l'application était 100 % auto-référentielle
-- (la charge cible dérive de la moyenne du joueur lui-même), donc elle savait dire « il s'entraîne
-- comme d'habitude » et jamais « il s'entraîne comme il faudrait ».
--
-- INVARIANT STRUCTURANT : un référentiel PUBLIÉ est immuable. Une correction crée une nouvelle
-- version, les clubs restent épinglés sur la leur et migrent quand ils le décident. Sans cette
-- règle, une retouche du super-admin déplacerait l'« Attendu » de tous les clubs d'un coup et un
-- joueur passerait de vert à rouge sans qu'aucun geste n'ait été fait — c'est exactement
-- l'incident déjà documenté sur `type_seance_cible` (« une couleur éditable au niveau du type
-- aurait repeint le calendrier de tous les clubs de la plateforme »).
--
-- Nouveau module activable `objectifs_performance` (add-on, cf. FeatureModule).
-- Double verrou : module actif ET permission `objectifs:read` / `objectifs:write`.
-- ============================================================
SET search_path = public;

-- ── Niveaux de compétition ──────────────────────────────────────────────────
-- Seul élément de vocabulaire ÉDITABLE par le super-admin. Les métriques et les postes, eux,
-- restent des enums Java : une métrique porte le nom d'une colonne réelle de `donnee_gps` et un
-- poste dépend du rabattement 11 → 6 codé dans PosteReference. Les rendre modifiables en base
-- permettrait d'inventer une entrée que rien ne sait calculer — et l'erreur serait invisible,
-- des zéros partout au lieu d'un message. Un niveau, lui, n'est qu'un libellé de regroupement.
CREATE TABLE IF NOT EXISTS niveau_competition (
    code    varchar(20) PRIMARY KEY,
    nom     varchar(80) NOT NULL,
    ordre   smallint NOT NULL DEFAULT 0,
    actif   boolean NOT NULL DEFAULT true
);

INSERT INTO niveau_competition (code, nom, ordre) VALUES
  ('N1',   'National 1',   1),
  ('N2',   'National 2',   2),
  ('N3',   'National 3',   3),
  ('R1',   'Régional 1',   4),
  ('R2',   'Régional 2',   5),
  ('R3',   'Régional 3',   6),
  ('U19N', 'U19 National', 7),
  ('U18',  'U18',          8),
  ('U17',  'U17',          9),
  ('U15',  'U15',         10)
ON CONFLICT (code) DO NOTHING;

-- ── En-tête d'un référentiel ────────────────────────────────────────────────
-- club_id NULL = référentiel PLATEFORME publié par le super-admin (catalogue partagé).
-- club_id renseigné = copie d'un club, personnalisée ; source_id garde le lien vers le standard
-- d'origine, ce qui permet d'afficher l'écart case par case.
CREATE TABLE IF NOT EXISTS referentiel_objectif (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id     uuid REFERENCES club (id) ON DELETE CASCADE,
    nom         varchar(160) NOT NULL,
    niveau      varchar(60),
    version     integer NOT NULL DEFAULT 1,
    parent_id   uuid REFERENCES referentiel_objectif (id) ON DELETE SET NULL,
    source_id   uuid REFERENCES referentiel_objectif (id) ON DELETE SET NULL,
    statut      varchar(20) NOT NULL DEFAULT 'BROUILLON',
    cree_par    uuid,
    created_at  timestamp NOT NULL DEFAULT now(),
    updated_at  timestamp NOT NULL DEFAULT now(),
    CONSTRAINT referentiel_objectif_statut_chk
        CHECK (statut IN ('BROUILLON', 'PUBLIE', 'ARCHIVE'))
);

CREATE INDEX IF NOT EXISTS idx_referentiel_objectif_club ON referentiel_objectif (club_id);

COMMENT ON TABLE referentiel_objectif IS
    'Norme de charge par niveau et par poste. club_id NULL = catalogue plateforme. Un PUBLIE est immuable.';

-- ── Les valeurs, à plat ─────────────────────────────────────────────────────
-- Table volontairement PLATE (poste et contexte portés ici plutôt que par une table
-- intermédiaire) : une seule jointure pour tout lire, et ajouter une métrique reste une entrée
-- d'enum côté Java, sans migration.
--
-- Deux contextes seulement, et SEMAINE INCLUT LE MATCH — c'est la lecture du document de
-- référence (« la semaine représente 2,8 à 3,5 fois la distance du match »). L'entraînement
-- n'est donc jamais stocké : il se DÉRIVE (semaine − minutes réellement jouées), ce qui fait
-- qu'un joueur resté sur le banc voit sa cible d'entraînement monter toute seule.
CREATE TABLE IF NOT EXISTS referentiel_objectif_valeur (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    referentiel_id  uuid NOT NULL REFERENCES referentiel_objectif (id) ON DELETE CASCADE,
    poste           varchar(40) NOT NULL,
    contexte        varchar(10) NOT NULL,
    metrique        varchar(40) NOT NULL,
    valeur_min      integer,
    valeur_max      integer,
    CONSTRAINT referentiel_objectif_valeur_contexte_chk CHECK (contexte IN ('MATCH', 'SEMAINE')),
    CONSTRAINT referentiel_objectif_valeur_unique UNIQUE (referentiel_id, poste, contexte, metrique)
);

CREATE INDEX IF NOT EXISTS idx_referentiel_valeur_ref ON referentiel_objectif_valeur (referentiel_id);

-- ── Adoption par un club, éventuellement par équipe ─────────────────────────
-- equipe_id NULL = référentiel par défaut du club ; renseigné = surcharge de cette équipe.
-- Ce n'est pas un raffinement : un club a des séniors en N1, une réserve en régional et des U19.
-- Une adoption unique de club serait fausse pour deux d'entre elles.
CREATE TABLE IF NOT EXISTS club_referentiel (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id         uuid NOT NULL REFERENCES club (id)   ON DELETE CASCADE,
    equipe_id       uuid          REFERENCES equipe (id) ON DELETE CASCADE,
    referentiel_id  uuid NOT NULL REFERENCES referentiel_objectif (id) ON DELETE CASCADE,
    adopte_par      uuid,
    updated_at      timestamp NOT NULL DEFAULT now(),
    CONSTRAINT club_referentiel_unique UNIQUE (club_id, equipe_id)
);

COMMENT ON TABLE club_referentiel IS
    'Quel référentiel s''applique à un club (equipe_id NULL) ou à une de ses équipes. Le club est épinglé sur SA version.';

-- ============================================================
-- SEED 1 — « National N1 », depuis le document de référence du préparateur.
-- Valeurs par poste sur un match de 90 min (§1) et sur une semaine type de compétition (§2).
-- ============================================================
INSERT INTO referentiel_objectif (id, club_id, nom, niveau, version, statut, created_at, updated_at)
VALUES ('b1000000-0000-0000-0000-000000000001', NULL, 'National N1', 'N1', 1, 'PUBLIE', now(), now())
ON CONFLICT (id) DO NOTHING;

-- Le GARDIEN et le NOMBRE DE SPRINTS sont volontairement ABSENTS : le document ne les fournit pas.
-- Une case vide est une donnée manquante que le club peut compléter ; une case inventée serait
-- une norme fausse que personne ne saurait remettre en cause.
INSERT INTO referentiel_objectif_valeur (referentiel_id, poste, contexte, metrique, valeur_min, valeur_max)
VALUES
  -- ── §1 : match de 90 minutes ──
  ('b1000000-0000-0000-0000-000000000001', 'defenseur_central', 'MATCH', 'distance_totale',  9500, 10500),
  ('b1000000-0000-0000-0000-000000000001', 'defenseur_central', 'MATCH', 'distance_15',      1100,  1500),
  ('b1000000-0000-0000-0000-000000000001', 'defenseur_central', 'MATCH', 'distance_19',       500,   700),
  ('b1000000-0000-0000-0000-000000000001', 'defenseur_central', 'MATCH', 'distance_24_28',    180,   280),
  ('b1000000-0000-0000-0000-000000000001', 'defenseur_central', 'MATCH', 'distance_28',        30,    80),

  ('b1000000-0000-0000-0000-000000000001', 'lateral',           'MATCH', 'distance_totale', 10500, 11500),
  ('b1000000-0000-0000-0000-000000000001', 'lateral',           'MATCH', 'distance_15',      1600,  2000),
  ('b1000000-0000-0000-0000-000000000001', 'lateral',           'MATCH', 'distance_19',       800,  1100),
  ('b1000000-0000-0000-0000-000000000001', 'lateral',           'MATCH', 'distance_24_28',    350,   500),
  ('b1000000-0000-0000-0000-000000000001', 'lateral',           'MATCH', 'distance_28',        80,   180),

  ('b1000000-0000-0000-0000-000000000001', 'milieu_axial',      'MATCH', 'distance_totale', 10800, 12000),
  ('b1000000-0000-0000-0000-000000000001', 'milieu_axial',      'MATCH', 'distance_15',      1500,  1900),
  ('b1000000-0000-0000-0000-000000000001', 'milieu_axial',      'MATCH', 'distance_19',       700,   950),
  ('b1000000-0000-0000-0000-000000000001', 'milieu_axial',      'MATCH', 'distance_24_28',    220,   350),
  ('b1000000-0000-0000-0000-000000000001', 'milieu_axial',      'MATCH', 'distance_28',        40,   100),

  ('b1000000-0000-0000-0000-000000000001', 'ailier',            'MATCH', 'distance_totale', 10200, 11300),
  ('b1000000-0000-0000-0000-000000000001', 'ailier',            'MATCH', 'distance_15',      1800,  2300),
  ('b1000000-0000-0000-0000-000000000001', 'ailier',            'MATCH', 'distance_19',       900,  1200),
  ('b1000000-0000-0000-0000-000000000001', 'ailier',            'MATCH', 'distance_24_28',    400,   650),
  ('b1000000-0000-0000-0000-000000000001', 'ailier',            'MATCH', 'distance_28',       120,   250),

  ('b1000000-0000-0000-0000-000000000001', 'attaquant',         'MATCH', 'distance_totale',  9800, 10800),
  ('b1000000-0000-0000-0000-000000000001', 'attaquant',         'MATCH', 'distance_15',      1500,  2000),
  ('b1000000-0000-0000-0000-000000000001', 'attaquant',         'MATCH', 'distance_19',       700,  1000),
  ('b1000000-0000-0000-0000-000000000001', 'attaquant',         'MATCH', 'distance_24_28',    300,   500),
  ('b1000000-0000-0000-0000-000000000001', 'attaquant',         'MATCH', 'distance_28',        80,   180),

  -- ── §2 : semaine type de compétition, MATCH COMPRIS ──
  ('b1000000-0000-0000-0000-000000000001', 'defenseur_central', 'SEMAINE', 'distance_totale', 29000, 33000),
  ('b1000000-0000-0000-0000-000000000001', 'defenseur_central', 'SEMAINE', 'distance_15',      3500,  4500),
  ('b1000000-0000-0000-0000-000000000001', 'defenseur_central', 'SEMAINE', 'distance_19',       900,  1300),
  ('b1000000-0000-0000-0000-000000000001', 'defenseur_central', 'SEMAINE', 'distance_24_28',    350,   500),
  ('b1000000-0000-0000-0000-000000000001', 'defenseur_central', 'SEMAINE', 'distance_28',        70,   140),

  ('b1000000-0000-0000-0000-000000000001', 'lateral',           'SEMAINE', 'distance_totale', 31000, 36000),
  ('b1000000-0000-0000-0000-000000000001', 'lateral',           'SEMAINE', 'distance_15',      4500,  5800),
  ('b1000000-0000-0000-0000-000000000001', 'lateral',           'SEMAINE', 'distance_19',      1400,  1900),
  ('b1000000-0000-0000-0000-000000000001', 'lateral',           'SEMAINE', 'distance_24_28',    600,   900),
  ('b1000000-0000-0000-0000-000000000001', 'lateral',           'SEMAINE', 'distance_28',       180,   350),

  ('b1000000-0000-0000-0000-000000000001', 'milieu_axial',      'SEMAINE', 'distance_totale', 33000, 37000),
  ('b1000000-0000-0000-0000-000000000001', 'milieu_axial',      'SEMAINE', 'distance_15',      4500,  5500),
  ('b1000000-0000-0000-0000-000000000001', 'milieu_axial',      'SEMAINE', 'distance_19',      1300,  1800),
  ('b1000000-0000-0000-0000-000000000001', 'milieu_axial',      'SEMAINE', 'distance_24_28',    500,   700),
  ('b1000000-0000-0000-0000-000000000001', 'milieu_axial',      'SEMAINE', 'distance_28',       120,   220),

  ('b1000000-0000-0000-0000-000000000001', 'ailier',            'SEMAINE', 'distance_totale', 31000, 35000),
  ('b1000000-0000-0000-0000-000000000001', 'ailier',            'SEMAINE', 'distance_15',      5000,  6000),
  ('b1000000-0000-0000-0000-000000000001', 'ailier',            'SEMAINE', 'distance_19',      1500,  2200),
  ('b1000000-0000-0000-0000-000000000001', 'ailier',            'SEMAINE', 'distance_24_28',    700,  1100),
  ('b1000000-0000-0000-0000-000000000001', 'ailier',            'SEMAINE', 'distance_28',       220,   450),

  ('b1000000-0000-0000-0000-000000000001', 'attaquant',         'SEMAINE', 'distance_totale', 30000, 34000),
  ('b1000000-0000-0000-0000-000000000001', 'attaquant',         'SEMAINE', 'distance_15',      4500,  5500),
  ('b1000000-0000-0000-0000-000000000001', 'attaquant',         'SEMAINE', 'distance_19',      1200,  1800),
  ('b1000000-0000-0000-0000-000000000001', 'attaquant',         'SEMAINE', 'distance_24_28',    550,   850),
  ('b1000000-0000-0000-0000-000000000001', 'attaquant',         'SEMAINE', 'distance_28',       180,   320)
ON CONFLICT ON CONSTRAINT referentiel_objectif_valeur_unique DO NOTHING;

-- Exposition à la vitesse max : un PIC, jamais un cumul. « 32 km/h » ne veut rien dire pour un
-- joueur qui plafonne à 30 → la cible s'exprime en % du RECORD PERSONNEL atteint au moins une
-- fois dans la semaine. 90 % est le conseil clé du document (« exposition régulière aux hautes
-- vitesses »). Pas de borne haute : il n'y a pas d'excès à courir vite une fois.
INSERT INTO referentiel_objectif_valeur (referentiel_id, poste, contexte, metrique, valeur_min, valeur_max)
SELECT 'b1000000-0000-0000-0000-000000000001', p.poste, 'SEMAINE', 'expo_vmax', 90, NULL
FROM (VALUES ('defenseur_central'), ('lateral'), ('milieu_axial'), ('ailier'), ('attaquant')) AS p(poste)
ON CONFLICT ON CONSTRAINT referentiel_objectif_valeur_unique DO NOTHING;

-- ============================================================
-- SEED 2 — « Régional N2–N3 », DÉRIVÉ du N1 à −10 %.
--
-- Pourquoi ce chiffre : les clés `objectif_<poste>` semées en V71 (m/min en match) valent, une
-- fois ramenées à 90 minutes, environ 10 % de moins que le document N1 — et ce, sur les cinq
-- postes (latéral 105 m/min → 9 450 m contre 10 500–11 500 au document). Ce ne sont donc pas des
-- valeurs fausses : ce sont les repères d'un niveau plus bas, et rien n'est jeté.
--
-- ⚠ C'est une EXTRAPOLATION, pas une source publiée : le super-admin peut (et devrait) la faire
-- valider puis la corriger via une nouvelle version. L'exposition V-max est exclue du calcul —
-- un pourcentage de record personnel ne se dégrade pas avec le niveau de la division.
-- ============================================================
INSERT INTO referentiel_objectif (id, club_id, nom, niveau, version, statut, created_at, updated_at)
VALUES ('b1000000-0000-0000-0000-000000000002', NULL, 'Régional N2–N3', 'N2', 1, 'PUBLIE', now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO referentiel_objectif_valeur (referentiel_id, poste, contexte, metrique, valeur_min, valeur_max)
SELECT 'b1000000-0000-0000-0000-000000000002', v.poste, v.contexte, v.metrique,
       CASE WHEN v.metrique = 'expo_vmax' THEN v.valeur_min
            ELSE (round(v.valeur_min * 0.9 / 10.0) * 10)::integer END,
       CASE WHEN v.valeur_max IS NULL THEN NULL
            ELSE (round(v.valeur_max * 0.9 / 10.0) * 10)::integer END
FROM referentiel_objectif_valeur v
WHERE v.referentiel_id = 'b1000000-0000-0000-0000-000000000001'
ON CONFLICT ON CONSTRAINT referentiel_objectif_valeur_unique DO NOTHING;

-- ── Module produit ──
INSERT INTO pack_module (pack_code, module_code) VALUES
 ('performance', 'objectifs_performance'),
 ('complet',     'objectifs_performance')
ON CONFLICT DO NOTHING;

-- ── Permissions ──
-- Fixer les objectifs de charge est un geste de PRÉPARATEUR (et d'entraîneur chef). L'entraîneur
-- les consulte sans les écrire ; le président n'est pas concerné — il ne dose pas la charge.
-- SUPER_ADMIN a tout d'office (bypass hors RBAC).
INSERT INTO role_permission (role_id, permission)
SELECT r.id, 'objectifs:read'
FROM (VALUES
   ('a0000000-0000-0000-0000-000000000002'::uuid),   -- ENTRAINEUR
   ('a0000000-0000-0000-0000-000000000006'::uuid),   -- ENTRAINEUR_CHEF
   ('a0000000-0000-0000-0000-000000000003'::uuid)    -- PREPARATEUR
) AS r(id)
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission)
SELECT r.id, 'objectifs:write'
FROM (VALUES
   ('a0000000-0000-0000-0000-000000000006'::uuid),   -- ENTRAINEUR_CHEF
   ('a0000000-0000-0000-0000-000000000003'::uuid)    -- PREPARATEUR
) AS r(id)
ON CONFLICT DO NOTHING;
