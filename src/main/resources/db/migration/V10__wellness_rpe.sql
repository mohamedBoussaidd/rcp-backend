-- ============================================================
-- V10 — Suivi subjectif du joueur : wellness quotidien + RPE de séance
--
-- wellness_quotidien : indice de Hooper (5 items sur 1..5), 1 saisie / jour.
-- rpe_seance         : effort perçu (Borg CR-10, 1..10) par séance ; la charge
--                      (rpe × durée) est calculée par l'appli et stockée.
--                      seance_id peut référencer la table `seance` (PHYSIQUE)
--                      OU `seance_technique` (TECHNIQUE) -> pas de FK dure ;
--                      seance_type indique la provenance, la durée est snapshotée.
-- ============================================================
SET search_path = public;

CREATE TABLE wellness_quotidien (
    id          uuid DEFAULT uuid_generate_v4() NOT NULL,
    joueur_id   uuid NOT NULL,
    equipe_id   uuid,
    date        date NOT NULL,
    sommeil     smallint NOT NULL,
    fatigue     smallint NOT NULL,
    douleur     smallint NOT NULL,
    stress      smallint NOT NULL,
    humeur      smallint NOT NULL,
    commentaire text,
    created_at  timestamp DEFAULT now() NOT NULL,
    CONSTRAINT wellness_quotidien_pkey PRIMARY KEY (id),
    CONSTRAINT wellness_quotidien_joueur_fkey FOREIGN KEY (joueur_id) REFERENCES joueur(id) ON DELETE CASCADE,
    CONSTRAINT wellness_quotidien_equipe_fkey FOREIGN KEY (equipe_id) REFERENCES equipe(id) ON DELETE SET NULL,
    CONSTRAINT wellness_quotidien_joueur_date_uk UNIQUE (joueur_id, date),
    CONSTRAINT wellness_sommeil_chk CHECK (sommeil BETWEEN 1 AND 5),
    CONSTRAINT wellness_fatigue_chk CHECK (fatigue BETWEEN 1 AND 5),
    CONSTRAINT wellness_douleur_chk CHECK (douleur BETWEEN 1 AND 5),
    CONSTRAINT wellness_stress_chk  CHECK (stress  BETWEEN 1 AND 5),
    CONSTRAINT wellness_humeur_chk  CHECK (humeur  BETWEEN 1 AND 5)
);
CREATE INDEX idx_wellness_joueur ON wellness_quotidien (joueur_id);
CREATE INDEX idx_wellness_equipe ON wellness_quotidien (equipe_id);

CREATE TABLE rpe_seance (
    id            uuid DEFAULT uuid_generate_v4() NOT NULL,
    joueur_id     uuid NOT NULL,
    equipe_id     uuid,
    seance_id     uuid NOT NULL,
    seance_type   varchar(20) NOT NULL,
    date          date NOT NULL,
    rpe           smallint NOT NULL,
    duree_minutes smallint,
    charge        integer,
    commentaire   text,
    created_at    timestamp DEFAULT now() NOT NULL,
    CONSTRAINT rpe_seance_pkey PRIMARY KEY (id),
    CONSTRAINT rpe_seance_joueur_fkey FOREIGN KEY (joueur_id) REFERENCES joueur(id) ON DELETE CASCADE,
    CONSTRAINT rpe_seance_equipe_fkey FOREIGN KEY (equipe_id) REFERENCES equipe(id) ON DELETE SET NULL,
    CONSTRAINT rpe_seance_joueur_seance_uk UNIQUE (joueur_id, seance_id),
    CONSTRAINT rpe_seance_rpe_chk CHECK (rpe BETWEEN 1 AND 10),
    CONSTRAINT rpe_seance_type_chk CHECK (seance_type IN ('PHYSIQUE', 'TECHNIQUE'))
);
CREATE INDEX idx_rpe_joueur ON rpe_seance (joueur_id);
CREATE INDEX idx_rpe_equipe ON rpe_seance (equipe_id);
