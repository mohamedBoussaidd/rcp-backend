-- ============================================================
-- V19 — Match (cycle de vie avant/après), niveau ÉQUIPE
-- Un match = une fiche de préparation (AVANT : adversaire, consignes,
-- schémas adverses, compo) puis de débrief (APRÈS : résultat, notes et
-- lien manuel vers une session GPS = une `seance` déjà importée).
-- Schémas attachés par COPIE du schema_json (snapshot, aucune synchro),
-- même principe que schema_tactique / plan de jeu.
-- ============================================================
SET search_path = public;

CREATE TABLE match_prepa (
    id              uuid DEFAULT uuid_generate_v4() NOT NULL,
    equipe_id       uuid NOT NULL,
    adversaire      varchar(120) NOT NULL,
    date_match      date,
    competition     varchar(120),
    domicile        boolean NOT NULL DEFAULT true,
    consignes       text,
    resultat        varchar(20),    -- VICTOIRE / NUL / DEFAITE (NULL = pas encore débriefé)
    score           varchar(20),
    notes_debrief   text,
    session_gps_id  uuid,           -- lien manuel vers une `seance` (NULL = non lié)
    cree_par        uuid,
    created_at      timestamp DEFAULT now() NOT NULL,
    updated_at      timestamp DEFAULT now() NOT NULL,
    CONSTRAINT match_prepa_pkey PRIMARY KEY (id),
    CONSTRAINT match_prepa_equipe_fkey  FOREIGN KEY (equipe_id)      REFERENCES equipe(id)       ON DELETE CASCADE,
    CONSTRAINT match_prepa_gps_fkey     FOREIGN KEY (session_gps_id) REFERENCES seance(id)       ON DELETE SET NULL,
    CONSTRAINT match_prepa_createur_fkey FOREIGN KEY (cree_par)      REFERENCES utilisateur(id)  ON DELETE SET NULL
);
CREATE INDEX idx_match_prepa_equipe ON match_prepa (equipe_id);

CREATE TABLE match_schema (
    id          uuid DEFAULT uuid_generate_v4() NOT NULL,
    match_id    uuid NOT NULL,
    titre       varchar(120),
    schema_json text NOT NULL,      -- copie (snapshot) du schéma
    apercu      text,               -- miniature PNG (data URL)
    ordre       int NOT NULL DEFAULT 0,
    created_at  timestamp DEFAULT now() NOT NULL,
    CONSTRAINT match_schema_pkey PRIMARY KEY (id),
    CONSTRAINT match_schema_match_fkey FOREIGN KEY (match_id) REFERENCES match_prepa(id) ON DELETE CASCADE
);
CREATE INDEX idx_match_schema_match ON match_schema (match_id);

CREATE TABLE match_compo (
    id          uuid DEFAULT uuid_generate_v4() NOT NULL,
    match_id    uuid NOT NULL,
    joueur_id   uuid NOT NULL,
    x           numeric(6,3) NOT NULL DEFAULT 0,   -- position relative sur le terrain [0..1]
    y           numeric(6,3) NOT NULL DEFAULT 0,
    statut      varchar(20) NOT NULL DEFAULT 'TITULAIRE',  -- TITULAIRE / REMPLACANT / RESERVE / REPOS / SUSPENDU
    CONSTRAINT match_compo_pkey PRIMARY KEY (id),
    CONSTRAINT match_compo_match_fkey  FOREIGN KEY (match_id)  REFERENCES match_prepa(id) ON DELETE CASCADE,
    CONSTRAINT match_compo_joueur_fkey FOREIGN KEY (joueur_id) REFERENCES joueur(id)      ON DELETE CASCADE,
    CONSTRAINT match_compo_unique UNIQUE (match_id, joueur_id)  -- un joueur une fois par compo
);
CREATE INDEX idx_match_compo_match ON match_compo (match_id);
