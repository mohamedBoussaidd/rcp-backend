-- ============================================================
-- V22 — Unification des seances
-- Fusionne seance_technique dans seance : une seance unique porte desormais
-- des exercices (reference + overrides), un objectif d'equipe, en plus du GPS
-- et de la presence. L'ancien modele technique est supprime (donnees jetables).
-- ============================================================
SET search_path = public;

-- ── 1) Exercice : type + attentes physiques optionnelles ──
ALTER TABLE exercice
    ADD COLUMN type                       varchar(20) NOT NULL DEFAULT 'TECHNIQUE',
    ADD COLUMN distance_attendue_m        integer,
    ADD COLUMN distance_haute_intensite_m integer,
    ADD COLUMN nb_sprints                 smallint;
ALTER TABLE exercice
    ADD CONSTRAINT exercice_type_check CHECK (type IN ('PHYSIQUE','TECHNIQUE','MIXTE'));

-- ── 2) Seance : objectif texte + objectifs de volume (equipe) + createur ──
ALTER TABLE seance
    ADD COLUMN objectif                            varchar(255),
    ADD COLUMN objectif_distance_m                 integer,
    ADD COLUMN objectif_intensite                  smallint,
    ADD COLUMN objectif_distance_haute_intensite_m integer,
    ADD COLUMN cree_par                            uuid;
ALTER TABLE seance
    ADD CONSTRAINT seance_createur_fkey FOREIGN KEY (cree_par) REFERENCES utilisateur(id) ON DELETE SET NULL;

-- ── 3) Lien seance <-> exercices : reference + overrides modifiables par seance ──
CREATE TABLE seance_exercice (
    id                         uuid DEFAULT uuid_generate_v4() NOT NULL,
    seance_id                  uuid NOT NULL,
    exercice_id                uuid NOT NULL,
    ordre                      smallint DEFAULT 0 NOT NULL,
    duree_minutes              smallint,
    intensite                  smallint,
    distance_attendue_m        integer,
    distance_haute_intensite_m integer,
    nb_sprints                 smallint,
    CONSTRAINT seance_exercice_pkey PRIMARY KEY (id),
    CONSTRAINT se_seance_fkey     FOREIGN KEY (seance_id)   REFERENCES seance(id)   ON DELETE CASCADE,
    CONSTRAINT se_exercice_fkey   FOREIGN KEY (exercice_id) REFERENCES exercice(id) ON DELETE CASCADE,
    CONSTRAINT se_intensite_check CHECK (intensite IS NULL OR (intensite BETWEEN 1 AND 5))
);
CREATE INDEX idx_seance_exercice_seance ON seance_exercice (seance_id);

-- ── 4) Un type de seance MATCH garanti ──
INSERT INTO type_seance (code, libelle) VALUES ('MATCH', 'Match')
ON CONFLICT (code) DO NOTHING;

-- ── 5) Suppression de l'ancien modele technique (donnees jetables) ──
DROP TABLE IF EXISTS seance_technique_exercice;
DROP TABLE IF EXISTS seance_technique;
