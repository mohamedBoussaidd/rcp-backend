-- ============================================================
-- V33 — Diaporama de séance (support de présentation réutilisable)
-- Un diaporama appartient à un club, éventuellement réservé à une équipe
-- (visibilite EQUIPE). Il est composé de slides ordonnées de 3 types :
--   SCHEMA      : copie d'un schéma tactique (schema_json + apercu, snapshot —
--                 même principe que section_plan / exercice : aucune synchro)
--   IMAGE       : image_src = URL externe OU data URL (upload)
--   VIDEO_LIEN  : video_url = lien YouTube / Vimeo
-- Édition réservée au créateur (cf. service) ; suppression possible aussi par
-- le détenteur de la permission diaporama:manage. Projeté en plein écran (TV).
-- ============================================================
SET search_path = public;

CREATE TABLE diaporama (
    id          uuid DEFAULT uuid_generate_v4() NOT NULL,
    club_id     uuid NOT NULL,
    equipe_id   uuid,                       -- NULL = visibilité club ; sinon équipe propriétaire
    titre       varchar(140) NOT NULL,
    visibilite  varchar(10)  NOT NULL DEFAULT 'CLUB',     -- 'CLUB' | 'EQUIPE'
    statut      varchar(10)  NOT NULL DEFAULT 'BROUILLON', -- 'BROUILLON' | 'PUBLIE'
    cree_par    uuid,
    created_at  timestamp DEFAULT now() NOT NULL,
    updated_at  timestamp DEFAULT now() NOT NULL,
    CONSTRAINT diaporama_pkey PRIMARY KEY (id),
    CONSTRAINT diaporama_club_fkey     FOREIGN KEY (club_id)   REFERENCES club(id)        ON DELETE CASCADE,
    CONSTRAINT diaporama_equipe_fkey   FOREIGN KEY (equipe_id) REFERENCES equipe(id)      ON DELETE CASCADE,
    CONSTRAINT diaporama_createur_fkey FOREIGN KEY (cree_par)  REFERENCES utilisateur(id) ON DELETE SET NULL
);
CREATE INDEX idx_diaporama_club   ON diaporama (club_id);
CREATE INDEX idx_diaporama_equipe ON diaporama (equipe_id);

CREATE TABLE slide (
    id            uuid DEFAULT uuid_generate_v4() NOT NULL,
    diaporama_id  uuid NOT NULL,
    ordre         int  NOT NULL DEFAULT 0,
    type          varchar(12) NOT NULL,     -- 'SCHEMA' | 'IMAGE' | 'VIDEO_LIEN'
    titre         varchar(140),
    schema_json   text,                     -- snapshot du schéma (type SCHEMA)
    apercu        text,                     -- miniature PNG (data URL) du schéma
    image_src     text,                     -- URL externe OU data URL (type IMAGE)
    video_url     varchar(500),             -- lien YouTube / Vimeo (type VIDEO_LIEN)
    created_at    timestamp DEFAULT now() NOT NULL,
    updated_at    timestamp DEFAULT now() NOT NULL,
    CONSTRAINT slide_pkey PRIMARY KEY (id),
    CONSTRAINT slide_diaporama_fkey FOREIGN KEY (diaporama_id) REFERENCES diaporama(id) ON DELETE CASCADE
);
CREATE INDEX idx_slide_diaporama ON slide (diaporama_id);

-- ── Permissions (seed RBAC, cf. V30) ───────────────────────────────────────
-- diaporama:read   → tout le staff (Président, Entraîneur, Préparateur, Médical)
-- diaporama:write  → Président, Entraîneur, Préparateur (créer / éditer les siens)
-- diaporama:manage → Président (supprimer / modérer toute diapo du club ; attribuable via matrice)
INSERT INTO role_permission (role_id, permission) VALUES
 ('a0000000-0000-0000-0000-000000000001','diaporama:read'),
 ('a0000000-0000-0000-0000-000000000002','diaporama:read'),
 ('a0000000-0000-0000-0000-000000000003','diaporama:read'),
 ('a0000000-0000-0000-0000-000000000004','diaporama:read'),
 ('a0000000-0000-0000-0000-000000000001','diaporama:write'),
 ('a0000000-0000-0000-0000-000000000002','diaporama:write'),
 ('a0000000-0000-0000-0000-000000000003','diaporama:write'),
 ('a0000000-0000-0000-0000-000000000001','diaporama:manage');
