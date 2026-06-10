-- ============================================================
-- V18 — Plan de jeu (« document d'identité équipe »), niveau ÉQUIPE
-- Document unique et vivant par équipe, composé de sections ordonnées et
-- éditables (phases de jeu). Chaque section peut porter UN schéma, attaché
-- par COPIE du schema_json (même principe que schema_tactique / exercice :
-- aucune synchro avec la bibliothèque une fois copié).
-- Le document et ses 6 sections standard sont créés à la volée par le
-- service au premier accès d'une équipe (pas de seed ici).
-- ============================================================
SET search_path = public;

CREATE TABLE plan_de_jeu (
    id          uuid DEFAULT uuid_generate_v4() NOT NULL,
    equipe_id   uuid NOT NULL,
    created_at  timestamp DEFAULT now() NOT NULL,
    updated_at  timestamp DEFAULT now() NOT NULL,
    CONSTRAINT plan_de_jeu_pkey PRIMARY KEY (id),
    CONSTRAINT plan_de_jeu_equipe_fkey   FOREIGN KEY (equipe_id) REFERENCES equipe(id) ON DELETE CASCADE,
    CONSTRAINT plan_de_jeu_equipe_unique  UNIQUE (equipe_id)  -- 1 document par équipe
);

CREATE TABLE section_plan (
    id            uuid DEFAULT uuid_generate_v4() NOT NULL,
    plan_de_jeu_id uuid NOT NULL,
    titre         varchar(120) NOT NULL,
    texte         text,
    schema_json   text,           -- NULL = section sans schéma (texte seul)
    apercu        text,           -- miniature PNG (data URL) du schéma
    ordre         int  NOT NULL DEFAULT 0,
    cree_par      uuid,
    created_at    timestamp DEFAULT now() NOT NULL,
    updated_at    timestamp DEFAULT now() NOT NULL,
    CONSTRAINT section_plan_pkey PRIMARY KEY (id),
    CONSTRAINT section_plan_plan_fkey     FOREIGN KEY (plan_de_jeu_id) REFERENCES plan_de_jeu(id)  ON DELETE CASCADE,
    CONSTRAINT section_plan_createur_fkey FOREIGN KEY (cree_par)       REFERENCES utilisateur(id)  ON DELETE SET NULL
);
CREATE INDEX idx_section_plan_plan ON section_plan (plan_de_jeu_id);
