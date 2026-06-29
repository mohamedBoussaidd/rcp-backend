-- ============================================================
-- V36 — Saison + Périodes typées + Effectif par saison
--
-- Donne un CADRE TEMPOREL explicite à l'app (jusqu'ici aucune notion de saison) :
--   saison           : une saison par ÉQUIPE (ouvrir/clôturer), borne tous les calculs
--   periode_saison   : phases typées (PREPARATION/COMPETITION/TREVE/REPRISE/INTERSAISON)
--                      → chaque type porte un PROFIL de surveillance (côté code Python)
--   effectif_saison  : appartenance d'un joueur à l'effectif d'UNE saison (≠ statut de dispo)
--
-- But : tuer les fausses alertes hors-saison/trêve, gérer la reconduction d'effectif
-- (transfert = absent de l'effectif suivant, données conservées), et faire repartir
-- les indicateurs « à neuf » chaque saison via le bornage.
--
-- blessure.retour_confirme : une blessure dont la date de retour est atteinte est
-- soldée AUTOMATIQUEMENT par le scheduler mais reste « à confirmer » par le staff.
--
-- Permissions : saison:read (tout le staff, pour le bandeau de période) +
-- saison:manage (président/entraîneur/préparateur). SUPER_ADMIN = bypass.
-- ============================================================
SET search_path = public;

-- ── Saison (au niveau équipe) ──────────────────────────────────────────────
CREATE TABLE saison (
    id          uuid DEFAULT uuid_generate_v4() NOT NULL,
    equipe_id   uuid         NOT NULL,
    libelle     varchar(40)  NOT NULL,                 -- ex. « 2026-2027 »
    date_debut  date         NOT NULL,
    date_fin    date         NOT NULL,
    statut      varchar(20)  NOT NULL DEFAULT 'EN_COURS',  -- PREPARATION | EN_COURS | CLOTUREE
    created_at  timestamp    DEFAULT now() NOT NULL,
    CONSTRAINT saison_pkey PRIMARY KEY (id),
    CONSTRAINT saison_equipe_fkey FOREIGN KEY (equipe_id) REFERENCES equipe(id) ON DELETE CASCADE,
    CONSTRAINT saison_dates_chk    CHECK (date_fin >= date_debut),
    CONSTRAINT saison_statut_chk   CHECK (statut IN ('PREPARATION','EN_COURS','CLOTUREE'))
);
CREATE INDEX idx_saison_equipe ON saison (equipe_id);
-- Au plus UNE saison EN_COURS par équipe.
CREATE UNIQUE INDEX uq_saison_en_cours ON saison (equipe_id) WHERE statut = 'EN_COURS';

-- ── Périodes typées d'une saison ───────────────────────────────────────────
CREATE TABLE periode_saison (
    id          uuid DEFAULT uuid_generate_v4() NOT NULL,
    saison_id   uuid         NOT NULL,
    type        varchar(20)  NOT NULL,                 -- PREPARATION|COMPETITION|TREVE|REPRISE|INTERSAISON
    libelle     varchar(60),                           -- défaut déduit du type si NULL
    date_debut  date         NOT NULL,
    date_fin    date         NOT NULL,
    ordre       smallint     NOT NULL DEFAULT 0,
    CONSTRAINT periode_saison_pkey PRIMARY KEY (id),
    CONSTRAINT periode_saison_saison_fkey FOREIGN KEY (saison_id) REFERENCES saison(id) ON DELETE CASCADE,
    CONSTRAINT periode_dates_chk  CHECK (date_fin >= date_debut),
    CONSTRAINT periode_type_chk   CHECK (type IN ('PREPARATION','COMPETITION','TREVE','REPRISE','INTERSAISON'))
);
CREATE INDEX idx_periode_saison ON periode_saison (saison_id);

-- ── Effectif d'une saison (appartenance ≠ statut de dispo) ──────────────────
CREATE TABLE effectif_saison (
    id             uuid DEFAULT uuid_generate_v4() NOT NULL,
    saison_id      uuid       NOT NULL,
    joueur_id      uuid       NOT NULL,
    date_entree    date,                               -- défaut = début de saison
    date_sortie    date,                               -- NULL = encore dans l'effectif
    numero_maillot smallint,
    CONSTRAINT effectif_saison_pkey PRIMARY KEY (id),
    CONSTRAINT effectif_saison_saison_fkey FOREIGN KEY (saison_id) REFERENCES saison(id)  ON DELETE CASCADE,
    CONSTRAINT effectif_saison_joueur_fkey FOREIGN KEY (joueur_id) REFERENCES joueur(id)  ON DELETE CASCADE,
    CONSTRAINT uq_effectif_saison UNIQUE (saison_id, joueur_id)
);
CREATE INDEX idx_effectif_saison_saison ON effectif_saison (saison_id);
CREATE INDEX idx_effectif_saison_joueur ON effectif_saison (joueur_id);

-- ── Blessure : retour auto soldé mais en attente de confirmation staff ──────
ALTER TABLE blessure ADD COLUMN retour_confirme boolean NOT NULL DEFAULT true;
-- true par défaut pour l'existant : seules les futures clôtures AUTOMATIQUES seront « à confirmer ».

-- ============================================================
-- Permissions (cf. V30 : enum Permission + role_permission)
--   saison:read   → 4 rôles staff (bandeau de période visible partout)
--   saison:manage → président / entraîneur / préparateur
-- ============================================================
INSERT INTO role_permission (role_id, permission)
SELECT r.id, 'saison:read'
FROM (VALUES
   ('a0000000-0000-0000-0000-000000000001'::uuid),   -- PRESIDENT
   ('a0000000-0000-0000-0000-000000000002'::uuid),   -- ENTRAINEUR
   ('a0000000-0000-0000-0000-000000000003'::uuid),   -- PREPARATEUR
   ('a0000000-0000-0000-0000-000000000004'::uuid)    -- MEDICAL
) AS r(id);

INSERT INTO role_permission (role_id, permission) VALUES
 ('a0000000-0000-0000-0000-000000000001','saison:manage'),
 ('a0000000-0000-0000-0000-000000000002','saison:manage'),
 ('a0000000-0000-0000-0000-000000000003','saison:manage');
