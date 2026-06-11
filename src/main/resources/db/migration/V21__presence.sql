-- ============================================================
-- V21 — Présence des joueurs par séance
-- L'entraîneur ou le préparateur coche chaque joueur de l'effectif
-- pour une séance donnée : PRESENT | ABSENT | EXCUSE | RETARD.
-- ============================================================
SET search_path = public;

CREATE TYPE statut_presence AS ENUM ('PRESENT', 'ABSENT', 'EXCUSE', 'RETARD');

CREATE TABLE presence (
    id          uuid DEFAULT uuid_generate_v4() NOT NULL,
    seance_id   uuid NOT NULL,
    joueur_id   uuid NOT NULL,
    statut      statut_presence NOT NULL DEFAULT 'PRESENT',
    note        text,
    created_at  timestamp DEFAULT now() NOT NULL,
    updated_at  timestamp DEFAULT now() NOT NULL,
    CONSTRAINT presence_pkey PRIMARY KEY (id),
    CONSTRAINT presence_seance_fkey  FOREIGN KEY (seance_id) REFERENCES seance(id)  ON DELETE CASCADE,
    CONSTRAINT presence_joueur_fkey  FOREIGN KEY (joueur_id) REFERENCES joueur(id)  ON DELETE CASCADE,
    CONSTRAINT presence_seance_joueur_unique UNIQUE (seance_id, joueur_id)
);

CREATE INDEX idx_presence_seance ON presence (seance_id);
CREATE INDEX idx_presence_joueur ON presence (joueur_id);
