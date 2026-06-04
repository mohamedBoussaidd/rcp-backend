-- ============================================================
-- V3 — Module technique de l'entraineur
-- Bibliotheque d'exercices (niveau CLUB) + seances techniques (niveau EQUIPE).
-- Les seances techniques n'entrent PAS dans les calculs GPS/charge.
-- ============================================================
SET search_path = public;

-- ── Bibliotheque d'exercices (partagee au sein du club) ──
CREATE TABLE exercice (
    id                uuid DEFAULT uuid_generate_v4() NOT NULL,
    club_id           uuid NOT NULL,
    nom               varchar(150) NOT NULL,
    categorie         varchar(40),        -- echauffement, technique, tactique, conservation, jeu_reduit, match_a_theme, finition, transition
    duree_minutes     smallint,
    objectif          varchar(255),
    intensite         smallint,           -- 1..5 (intensite demandee)
    description       text,
    cree_par          uuid,               -- utilisateur createur (pour affichage + filtre + droit d'edition)
    equipe_origine_id uuid,               -- equipe du createur (pour filtre)
    created_at        timestamp DEFAULT now() NOT NULL,
    CONSTRAINT exercice_pkey PRIMARY KEY (id),
    CONSTRAINT exercice_club_fkey          FOREIGN KEY (club_id)           REFERENCES club(id)        ON DELETE CASCADE,
    CONSTRAINT exercice_createur_fkey      FOREIGN KEY (cree_par)          REFERENCES utilisateur(id) ON DELETE SET NULL,
    CONSTRAINT exercice_equipe_orig_fkey   FOREIGN KEY (equipe_origine_id) REFERENCES equipe(id)      ON DELETE SET NULL,
    CONSTRAINT exercice_intensite_check    CHECK (intensite IS NULL OR (intensite BETWEEN 1 AND 5))
);
CREATE INDEX idx_exercice_club ON exercice (club_id);

-- ── Seance technique (datee, sur le calendrier d'une equipe) ──
CREATE TABLE seance_technique (
    id          uuid DEFAULT uuid_generate_v4() NOT NULL,
    equipe_id   uuid NOT NULL,
    date        date NOT NULL,
    heure_debut time,
    titre       varchar(150),
    objectif    varchar(255),
    statut      varchar(20) DEFAULT 'PLANIFIEE' NOT NULL,
    cree_par    uuid,
    created_at  timestamp DEFAULT now() NOT NULL,
    CONSTRAINT seance_technique_pkey PRIMARY KEY (id),
    CONSTRAINT seance_technique_equipe_fkey   FOREIGN KEY (equipe_id) REFERENCES equipe(id)      ON DELETE CASCADE,
    CONSTRAINT seance_technique_createur_fkey FOREIGN KEY (cree_par)  REFERENCES utilisateur(id) ON DELETE SET NULL,
    CONSTRAINT seance_technique_statut_check  CHECK (statut IN ('PLANIFIEE','REALISEE','ANNULEE'))
);
CREATE INDEX idx_seance_technique_equipe ON seance_technique (equipe_id);
CREATE INDEX idx_seance_technique_date   ON seance_technique (date);

-- ── Lien seance technique <-> exercices (ordonne) ──
CREATE TABLE seance_technique_exercice (
    id                  uuid DEFAULT uuid_generate_v4() NOT NULL,
    seance_technique_id uuid NOT NULL,
    exercice_id         uuid NOT NULL,
    ordre               smallint DEFAULT 0 NOT NULL,
    CONSTRAINT seance_technique_exercice_pkey PRIMARY KEY (id),
    CONSTRAINT ste_seance_fkey   FOREIGN KEY (seance_technique_id) REFERENCES seance_technique(id) ON DELETE CASCADE,
    CONSTRAINT ste_exercice_fkey FOREIGN KEY (exercice_id)         REFERENCES exercice(id)         ON DELETE CASCADE
);
CREATE INDEX idx_ste_seance ON seance_technique_exercice (seance_technique_id);
