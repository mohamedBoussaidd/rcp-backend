-- ============================================================
-- V47 — Licences & documents administratifs
--
-- Nouveau domaine `documentadmin` : conformité documentaire de l'effectif (licence,
-- certificat médical, autorisation parentale, pièce d'identité...), avec un référentiel
-- configurable PAR CLUB et un workflow SOUMIS → VALIDE/REFUSE, expiration automatique.
--
--   categorie_age        : référentiel d'âge configurable par club (U15, U17...), bornes en
--                          ÂGE ATTEINT DANS LA SAISON (pas en année de naissance figée) — ne
--                          nécessite aucune retouche d'une saison à l'autre. Sert à filtrer les
--                          documents applicables à un joueur d'après sa date de naissance RÉELLE,
--                          indépendamment de son équipe (couvre le cas d'un joueur surclassé).
--   type_document_requis : référentiel des documents exigés (obligatoire, validation manuelle
--                          ou auto, durée de validité, catégories d'âge concernées).
--   document_joueur      : un document déposé pour un joueur. Le statut MANQUANT n'est JAMAIS
--                          stocké : il correspond à l'absence de ligne pour un (joueur, type)
--                          applicable — évite de matérialiser des lignes vides à chaque joueur
--                          ou type ajouté. Écrasement simple au redépôt (pas d'historique des
--                          refus, cohérent avec document_medical).
--
-- Fichiers hors web-root (app.documentadmin.upload-dir), même pattern que document_medical.
--
-- Couche produit : module `documents_admin` (add-on pur, hors packs seed — activable club par
-- club via club_module). RBAC : 4 permissions docadmin:* seedées ci-dessous.
-- ============================================================
SET search_path = public;

-- ── Catégories d'âge (référentiel configurable par club) ────────────────────
CREATE TABLE categorie_age (
    id       uuid DEFAULT uuid_generate_v4() NOT NULL,
    club_id  uuid NOT NULL,
    code     varchar(20) NOT NULL,
    libelle  varchar(60) NOT NULL,
    age_min  smallint,                        -- âge atteint dans la saison (inclusif) ; null = pas de plancher
    age_max  smallint,                        -- âge atteint dans la saison (inclusif) ; null = pas de plafond
    ordre    smallint NOT NULL DEFAULT 0,
    actif    boolean  NOT NULL DEFAULT true,
    CONSTRAINT categorie_age_pkey PRIMARY KEY (id),
    CONSTRAINT categorie_age_club_fkey FOREIGN KEY (club_id) REFERENCES club(id) ON DELETE CASCADE,
    CONSTRAINT categorie_age_uq UNIQUE (club_id, code),
    CONSTRAINT categorie_age_bornes_chk CHECK (age_min IS NULL OR age_max IS NULL OR age_min <= age_max)
);
CREATE INDEX idx_categorie_age_club ON categorie_age (club_id);

-- ── Types de documents requis (référentiel configurable par club) ───────────
CREATE TABLE type_document_requis (
    id                   uuid DEFAULT uuid_generate_v4() NOT NULL,
    club_id              uuid NOT NULL,
    code                 varchar(40)  NOT NULL,
    libelle              varchar(120) NOT NULL,
    description          text,
    obligatoire          boolean  NOT NULL DEFAULT true,
    validation_manuelle  boolean  NOT NULL DEFAULT true,
    duree_validite_mois  smallint,                 -- null = pas d'expiration
    categories_age       text,                     -- CSV de categorie_age.code ; null = toutes catégories
    actif                boolean  NOT NULL DEFAULT true,
    ordre                smallint NOT NULL DEFAULT 0,
    CONSTRAINT type_document_requis_pkey PRIMARY KEY (id),
    CONSTRAINT type_document_requis_club_fkey FOREIGN KEY (club_id) REFERENCES club(id) ON DELETE CASCADE,
    CONSTRAINT type_document_requis_uq UNIQUE (club_id, code)
);
CREATE INDEX idx_type_document_requis_club ON type_document_requis (club_id);

-- ── Documents déposés pour un joueur ──────────────────────────────────────────
CREATE TABLE document_joueur (
    id                       uuid DEFAULT uuid_generate_v4() NOT NULL,
    club_id                  uuid NOT NULL,
    joueur_id                uuid NOT NULL,
    type_document_requis_id  uuid NOT NULL,
    statut                   varchar(12) NOT NULL,     -- 'SOUMIS' | 'VALIDE' | 'REFUSE' | 'EXPIRE'
    chemin_stockage          varchar(255),
    nom_original             varchar(255),
    type_mime                varchar(100),
    taille_octets            bigint,
    date_soumission          timestamp,
    date_validation          timestamp,
    valide_par               uuid,
    motif_refus              text,
    date_expiration          date,
    created_at               timestamp NOT NULL DEFAULT now(),
    updated_at               timestamp NOT NULL DEFAULT now(),
    CONSTRAINT document_joueur_pkey PRIMARY KEY (id),
    CONSTRAINT document_joueur_statut_chk CHECK (statut IN ('SOUMIS','VALIDE','REFUSE','EXPIRE')),
    CONSTRAINT document_joueur_club_fkey   FOREIGN KEY (club_id)                 REFERENCES club(id)                 ON DELETE CASCADE,
    CONSTRAINT document_joueur_joueur_fkey FOREIGN KEY (joueur_id)               REFERENCES joueur(id)               ON DELETE CASCADE,
    CONSTRAINT document_joueur_type_fkey   FOREIGN KEY (type_document_requis_id) REFERENCES type_document_requis(id) ON DELETE CASCADE,
    CONSTRAINT document_joueur_valide_par_fkey FOREIGN KEY (valide_par) REFERENCES utilisateur(id) ON DELETE SET NULL,
    CONSTRAINT document_joueur_uq UNIQUE (joueur_id, type_document_requis_id)
);
CREATE INDEX idx_document_joueur_joueur ON document_joueur (joueur_id);
CREATE INDEX idx_document_joueur_club   ON document_joueur (club_id);
CREATE INDEX idx_document_joueur_type   ON document_joueur (type_document_requis_id);

-- ── Seed : catégories d'âge par défaut pour tous les clubs existants ─────────
INSERT INTO categorie_age (club_id, code, libelle, age_min, age_max, ordre)
SELECT c.id, v.code, v.libelle, v.age_min, v.age_max, v.ordre
FROM club c
CROSS JOIN (VALUES
    ('U9',     'Moins de 9 ans',   NULL::smallint, 9::smallint,    1::smallint),
    ('U11',    'Moins de 11 ans',  10::smallint,   11::smallint,   2::smallint),
    ('U13',    'Moins de 13 ans',  12::smallint,   13::smallint,   3::smallint),
    ('U15',    'Moins de 15 ans',  14::smallint,   15::smallint,   4::smallint),
    ('U17',    'Moins de 17 ans',  16::smallint,   17::smallint,   5::smallint),
    ('U19',    'Moins de 19 ans',  18::smallint,   19::smallint,   6::smallint),
    ('SENIOR', 'Senior',           20::smallint,   NULL::smallint, 7::smallint)
) AS v(code, libelle, age_min, age_max, ordre);

-- ── Seed : types de documents par défaut pour tous les clubs existants ───────
INSERT INTO type_document_requis (club_id, code, libelle, description, obligatoire, validation_manuelle, duree_validite_mois, categories_age, ordre)
SELECT c.id, v.code, v.libelle, v.description, v.obligatoire, v.validation_manuelle, v.duree_validite_mois, v.categories_age, v.ordre
FROM club c
CROSS JOIN (VALUES
    ('licence', 'Licence FFF',
     'Licence fédérale de la saison en cours',
     true, true, 12::smallint, NULL::text, 1::smallint),
    ('certificat_medical', 'Certificat médical',
     'Certificat de non contre-indication à la pratique du football',
     true, true, 12::smallint, NULL::text, 2::smallint),
    ('autorisation_parentale', 'Autorisation parentale',
     'Autorisation parentale de pratique et de déplacement (joueurs mineurs)',
     true, true, NULL::smallint, 'U9,U11,U13,U15,U17'::text, 3::smallint),
    ('piece_identite', 'Pièce d''identité',
     'Copie recto-verso d''une pièce d''identité en cours de validité',
     true, true, NULL::smallint, NULL::text, 4::smallint)
) AS v(code, libelle, description, obligatoire, validation_manuelle, duree_validite_mois, categories_age, ordre);

-- ── Couche produit : module add-on pur, hors packs seed (activation manuelle club par club) ──
-- (aucun INSERT INTO pack_module : cf. FeatureModule.DOCUMENTS_ADMIN, non socle, non listé dans un pack)

-- ── RBAC : permissions du domaine (cf. Permission.java) ─────────────────────
-- Rôles système (club_id NULL) : 001 Président, 002 Entraîneur, 003 Préparateur,
-- 004 Médical, 005 Administratif, 006 Entraîneur en chef.
--   docadmin:read                              → Président, Entraîneur, Préparateur, Médical, Administratif, Chef
--   docadmin:configure/validate/upload         → Président, Administratif UNIQUEMENT
INSERT INTO role_permission (role_id, permission) VALUES
 ('a0000000-0000-0000-0000-000000000001','docadmin:read'),
 ('a0000000-0000-0000-0000-000000000002','docadmin:read'),
 ('a0000000-0000-0000-0000-000000000003','docadmin:read'),
 ('a0000000-0000-0000-0000-000000000004','docadmin:read'),
 ('a0000000-0000-0000-0000-000000000005','docadmin:read'),
 ('a0000000-0000-0000-0000-000000000006','docadmin:read'),
 ('a0000000-0000-0000-0000-000000000001','docadmin:configure'),
 ('a0000000-0000-0000-0000-000000000005','docadmin:configure'),
 ('a0000000-0000-0000-0000-000000000001','docadmin:validate'),
 ('a0000000-0000-0000-0000-000000000005','docadmin:validate'),
 ('a0000000-0000-0000-0000-000000000001','docadmin:upload'),
 ('a0000000-0000-0000-0000-000000000005','docadmin:upload');
