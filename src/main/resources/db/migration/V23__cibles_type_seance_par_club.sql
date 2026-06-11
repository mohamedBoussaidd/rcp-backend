-- ============================================================
-- V23 — Cibles d'équipe par type de séance, propres à chaque club
-- Override par club des cibles physiques (distance / haute intensité / intensité)
-- qui pré-remplissent le formulaire de séance selon le type choisi.
-- ============================================================
SET search_path = public;

CREATE TABLE type_seance_cible (
    id                                   uuid DEFAULT uuid_generate_v4() NOT NULL,
    club_id                              uuid NOT NULL,
    type_seance_id                       uuid NOT NULL,
    objectif_distance_m                  integer,
    objectif_distance_haute_intensite_m  integer,
    objectif_intensite                   smallint,
    CONSTRAINT type_seance_cible_pkey PRIMARY KEY (id),
    CONSTRAINT tsc_club_fkey  FOREIGN KEY (club_id)        REFERENCES club(id)         ON DELETE CASCADE,
    CONSTRAINT tsc_type_fkey  FOREIGN KEY (type_seance_id) REFERENCES type_seance(id)  ON DELETE CASCADE,
    CONSTRAINT tsc_unique     UNIQUE (club_id, type_seance_id),
    CONSTRAINT tsc_intensite_check CHECK (objectif_intensite IS NULL OR (objectif_intensite BETWEEN 1 AND 5))
);
CREATE INDEX idx_type_seance_cible_club ON type_seance_cible (club_id);
