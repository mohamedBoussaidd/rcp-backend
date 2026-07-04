-- ============================================================
-- V44 — Suivi individuel & Entretiens
--
-- Nouveau domaine « entretien » : accompagnement individuel du joueur.
--   axe_travail    : axe de progression suivi pour un joueur (technique/tactique/…)
--   entretien      : entretien individuel (vidéo / terrain / discussion), mené par un membre
--                    du staff, visible STAFF par défaut ; partage au joueur = action explicite.
--   entretien_axe  : jointure porteuse — pour un entretien, l'évaluation d'un axe (note 1..5 +
--                    tendance + commentaire).
--   auto_evaluation: le joueur s'auto-évalue sur un de SES axes, hors entretien.
--
-- Données rattachées à la FICHE joueur (joueur_id), jamais au compte utilisateur : le suivi
-- survit à l'absence de compte, et le partage devient visible dès l'activation du compte.
--
-- Couche produit : module activable `suivi_individuel` (packs Performance + Complet, add-on
-- possible par club via club_module). RBAC : 5 permissions entretien:*/axe:* seedées ci-dessous.
-- ============================================================
SET search_path = public;

-- ── Axes de travail (progression suivie d'un joueur) ────────────────────────
CREATE TABLE axe_travail (
    id          uuid DEFAULT uuid_generate_v4() NOT NULL,
    joueur_id   uuid NOT NULL,
    club_id     uuid NOT NULL,
    libelle     varchar(140) NOT NULL,
    categorie   varchar(12)  NOT NULL,              -- 'TECHNIQUE' | 'TACTIQUE' | 'MENTAL' | 'PHYSIQUE'
    statut      varchar(12)  NOT NULL DEFAULT 'EN_COURS', -- 'EN_COURS' | 'ACQUIS' | 'ABANDONNE'
    cree_par    uuid,
    created_at  timestamp DEFAULT now() NOT NULL,
    updated_at  timestamp DEFAULT now() NOT NULL,
    CONSTRAINT axe_travail_pkey PRIMARY KEY (id),
    CONSTRAINT axe_travail_joueur_fkey   FOREIGN KEY (joueur_id) REFERENCES joueur(id)      ON DELETE CASCADE,
    CONSTRAINT axe_travail_club_fkey     FOREIGN KEY (club_id)   REFERENCES club(id)         ON DELETE CASCADE,
    CONSTRAINT axe_travail_createur_fkey FOREIGN KEY (cree_par)  REFERENCES utilisateur(id)  ON DELETE SET NULL
);
CREATE INDEX idx_axe_travail_joueur ON axe_travail (joueur_id);
CREATE INDEX idx_axe_travail_club   ON axe_travail (club_id);

-- ── Entretiens individuels ──────────────────────────────────────────────────
CREATE TABLE entretien (
    id                 uuid DEFAULT uuid_generate_v4() NOT NULL,
    joueur_id          uuid NOT NULL,
    club_id            uuid NOT NULL,
    equipe_id          uuid,
    type               varchar(12) NOT NULL,        -- 'VIDEO' | 'TERRAIN' | 'DISCUSSION'
    date_entretien     date NOT NULL,
    mene_par           uuid,
    notes              text,
    visibilite         varchar(16) NOT NULL DEFAULT 'STAFF',   -- 'STAFF' | 'PARTAGE_JOUEUR'
    seance_id          uuid,
    schema_tactique_id uuid,
    video_url          varchar(500),
    created_at         timestamp DEFAULT now() NOT NULL,
    updated_at         timestamp DEFAULT now() NOT NULL,
    CONSTRAINT entretien_pkey PRIMARY KEY (id),
    CONSTRAINT entretien_joueur_fkey  FOREIGN KEY (joueur_id)          REFERENCES joueur(id)          ON DELETE CASCADE,
    CONSTRAINT entretien_club_fkey    FOREIGN KEY (club_id)            REFERENCES club(id)            ON DELETE CASCADE,
    CONSTRAINT entretien_equipe_fkey  FOREIGN KEY (equipe_id)          REFERENCES equipe(id)          ON DELETE SET NULL,
    CONSTRAINT entretien_mene_fkey    FOREIGN KEY (mene_par)           REFERENCES utilisateur(id)     ON DELETE SET NULL,
    CONSTRAINT entretien_seance_fkey  FOREIGN KEY (seance_id)          REFERENCES seance(id)          ON DELETE SET NULL,
    CONSTRAINT entretien_schema_fkey  FOREIGN KEY (schema_tactique_id) REFERENCES schema_tactique(id) ON DELETE SET NULL
);
CREATE INDEX idx_entretien_joueur ON entretien (joueur_id);
CREATE INDEX idx_entretien_club   ON entretien (club_id);
CREATE INDEX idx_entretien_equipe ON entretien (equipe_id);

-- ── Évaluation d'un axe dans un entretien (jointure porteuse) ────────────────
CREATE TABLE entretien_axe (
    id             uuid DEFAULT uuid_generate_v4() NOT NULL,
    entretien_id   uuid NOT NULL,
    axe_travail_id uuid NOT NULL,
    note           int,                              -- 1..5, nullable
    tendance       varchar(12),                      -- 'EN_PROGRES' | 'STAGNE' | 'REGRESSE', nullable
    commentaire    text,
    CONSTRAINT entretien_axe_pkey PRIMARY KEY (id),
    CONSTRAINT entretien_axe_note_chk CHECK (note IS NULL OR (note BETWEEN 1 AND 5)),
    CONSTRAINT entretien_axe_uq UNIQUE (entretien_id, axe_travail_id),
    CONSTRAINT entretien_axe_entretien_fkey FOREIGN KEY (entretien_id)   REFERENCES entretien(id)    ON DELETE CASCADE,
    CONSTRAINT entretien_axe_axe_fkey       FOREIGN KEY (axe_travail_id) REFERENCES axe_travail(id)  ON DELETE CASCADE
);
CREATE INDEX idx_entretien_axe_entretien ON entretien_axe (entretien_id);
CREATE INDEX idx_entretien_axe_axe       ON entretien_axe (axe_travail_id);

-- ── Auto-évaluation du joueur (hors entretien) ──────────────────────────────
CREATE TABLE auto_evaluation (
    id             uuid DEFAULT uuid_generate_v4() NOT NULL,
    joueur_id      uuid NOT NULL,
    axe_travail_id uuid NOT NULL,
    note           int  NOT NULL,                    -- 1..5
    commentaire    text,
    created_at     timestamp DEFAULT now() NOT NULL,
    CONSTRAINT auto_evaluation_pkey PRIMARY KEY (id),
    CONSTRAINT auto_evaluation_note_chk CHECK (note BETWEEN 1 AND 5),
    CONSTRAINT auto_evaluation_joueur_fkey FOREIGN KEY (joueur_id)      REFERENCES joueur(id)      ON DELETE CASCADE,
    CONSTRAINT auto_evaluation_axe_fkey    FOREIGN KEY (axe_travail_id) REFERENCES axe_travail(id) ON DELETE CASCADE
);
CREATE INDEX idx_auto_evaluation_joueur ON auto_evaluation (joueur_id);
CREATE INDEX idx_auto_evaluation_axe    ON auto_evaluation (axe_travail_id);

-- ── Alerte « joueur sans entretien récent » (par équipe, cf. notif_config_equipe) ──
ALTER TABLE notif_config_equipe
    ADD COLUMN entretien_alerte_active boolean  NOT NULL DEFAULT true,
    ADD COLUMN entretien_seuil_jours   smallint NOT NULL DEFAULT 42;   -- 6 semaines

-- ── Couche produit : module activable dans les packs Performance + Complet ──
INSERT INTO pack_module (pack_code, module_code) VALUES
 ('performance','suivi_individuel'),
 ('complet','suivi_individuel');

-- ── RBAC : permissions du domaine (cf. Permission.java) ─────────────────────
-- Rôles système (club_id NULL) : 001 Président, 002 Entraîneur, 003 Préparateur,
-- 004 Médical, 005 Administratif, 006 Entraîneur en chef.
--   entretien:read / axe:read  → Président, Entraîneur, Préparateur, Médical, Chef
--   entretien:write / axe:write→ Président, Entraîneur, Préparateur, Chef
--   entretien:manage           → Président, Chef (supprimer/modérer les entretiens des AUTRES ;
--                                 l'auteur supprime les siens avec write)
INSERT INTO role_permission (role_id, permission) VALUES
 -- Lectures (5 rôles staff hors Administratif)
 ('a0000000-0000-0000-0000-000000000001','entretien:read'),
 ('a0000000-0000-0000-0000-000000000002','entretien:read'),
 ('a0000000-0000-0000-0000-000000000003','entretien:read'),
 ('a0000000-0000-0000-0000-000000000004','entretien:read'),
 ('a0000000-0000-0000-0000-000000000006','entretien:read'),
 ('a0000000-0000-0000-0000-000000000001','axe:read'),
 ('a0000000-0000-0000-0000-000000000002','axe:read'),
 ('a0000000-0000-0000-0000-000000000003','axe:read'),
 ('a0000000-0000-0000-0000-000000000004','axe:read'),
 ('a0000000-0000-0000-0000-000000000006','axe:read'),
 -- Écritures (Président, Entraîneur, Préparateur, Chef)
 ('a0000000-0000-0000-0000-000000000001','entretien:write'),
 ('a0000000-0000-0000-0000-000000000002','entretien:write'),
 ('a0000000-0000-0000-0000-000000000003','entretien:write'),
 ('a0000000-0000-0000-0000-000000000006','entretien:write'),
 ('a0000000-0000-0000-0000-000000000001','axe:write'),
 ('a0000000-0000-0000-0000-000000000002','axe:write'),
 ('a0000000-0000-0000-0000-000000000003','axe:write'),
 ('a0000000-0000-0000-0000-000000000006','axe:write'),
 -- Modération (Président, Chef)
 ('a0000000-0000-0000-0000-000000000001','entretien:manage'),
 ('a0000000-0000-0000-0000-000000000006','entretien:manage');
