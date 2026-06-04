-- ============================================================
-- V8 — Formations personnalisées (tactiques enregistrées), niveau club
-- positions_json : positions normalisées des joueurs (0..1) du tableau tactique.
-- ============================================================
SET search_path = public;

CREATE TABLE formation (
    id             uuid DEFAULT uuid_generate_v4() NOT NULL,
    club_id        uuid NOT NULL,
    nom            varchar(80) NOT NULL,
    couleur        varchar(20),
    positions_json text NOT NULL,
    cree_par       uuid,
    created_at     timestamp DEFAULT now() NOT NULL,
    CONSTRAINT formation_pkey PRIMARY KEY (id),
    CONSTRAINT formation_club_fkey     FOREIGN KEY (club_id)  REFERENCES club(id)        ON DELETE CASCADE,
    CONSTRAINT formation_createur_fkey FOREIGN KEY (cree_par) REFERENCES utilisateur(id) ON DELETE SET NULL
);
CREATE INDEX idx_formation_club ON formation (club_id);
