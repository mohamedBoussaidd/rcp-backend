-- ============================================================
-- V17 — Schémas tactiques (bibliothèque), niveau club
-- Bibliothèque de schémas réutilisables (phases de jeu, CPA…), éditables
-- avec le même éditeur que les schémas d'exercice. Le rattachement à un
-- exercice se fait par COPIE du schema_json (cf. exercice.schema_json) :
-- modifier la copie d'un exercice ne touche pas le schéma de base ici.
-- ============================================================
SET search_path = public;

CREATE TABLE schema_tactique (
    id          uuid DEFAULT uuid_generate_v4() NOT NULL,
    club_id     uuid NOT NULL,
    nom         varchar(120) NOT NULL,
    categorie   varchar(40),
    schema_json text NOT NULL,
    apercu      text,            -- miniature PNG (data URL) pour la grille de la bibliothèque
    cree_par    uuid,
    created_at  timestamp DEFAULT now() NOT NULL,
    updated_at  timestamp DEFAULT now() NOT NULL,
    CONSTRAINT schema_tactique_pkey PRIMARY KEY (id),
    CONSTRAINT schema_tactique_club_fkey     FOREIGN KEY (club_id)  REFERENCES club(id)        ON DELETE CASCADE,
    CONSTRAINT schema_tactique_createur_fkey FOREIGN KEY (cree_par) REFERENCES utilisateur(id) ON DELETE SET NULL
);
CREATE INDEX idx_schema_tactique_club ON schema_tactique (club_id);
