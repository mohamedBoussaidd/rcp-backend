-- ============================================================
-- V35 — Modèles de semaine (gabarits hebdomadaires par équipe)
--
-- Une équipe peut définir plusieurs « modèles de semaine » nommés (ex. « Semaine
-- championnat », « Semaine de prépa », « Semaine double match »). Chaque modèle
-- contient des créneaux-jours porteurs du cadre logistique + de l'objectif/charge
-- cible par défaut. On INSTANCIE un modèle sur une plage de dates : cela génère de
-- vraies séances (table seance), ensuite modifiables au cas par cas SANS toucher au
-- modèle (snapshot, aucune synchro — même principe que diaporama/exercice).
--
-- Permissions : réutilise seances:read / seances:write (cf. V30) — pas de nouvelle
-- capability, les rôles qui gèrent déjà les séances gèrent les modèles.
-- ============================================================
SET search_path = public;

CREATE TABLE modele_semaine (
    id          uuid DEFAULT uuid_generate_v4() NOT NULL,
    equipe_id   uuid NOT NULL,
    nom         varchar(100) NOT NULL,
    description varchar(255),
    cree_par    uuid,
    created_at  timestamp DEFAULT now() NOT NULL,
    updated_at  timestamp DEFAULT now() NOT NULL,
    CONSTRAINT modele_semaine_pkey PRIMARY KEY (id),
    CONSTRAINT modele_semaine_equipe_fkey  FOREIGN KEY (equipe_id) REFERENCES equipe(id)      ON DELETE CASCADE,
    CONSTRAINT modele_semaine_createur_fkey FOREIGN KEY (cree_par) REFERENCES utilisateur(id) ON DELETE SET NULL
);
CREATE INDEX idx_modele_semaine_equipe ON modele_semaine (equipe_id);

CREATE TABLE creneau_modele (
    id                 uuid DEFAULT uuid_generate_v4() NOT NULL,
    modele_id          uuid NOT NULL,
    jour_semaine       smallint NOT NULL,          -- ISO : 1 = lundi … 7 = dimanche
    heure_debut        time,
    duree_minutes      smallint,
    terrain            varchar(120),
    type_seance_id     uuid NOT NULL,
    titre              varchar(140),
    objectif           varchar(255),               -- objectif / charge cible textuel par défaut
    objectif_distance_m            integer,
    objectif_intensite             smallint,
    ordre              smallint NOT NULL DEFAULT 0,
    CONSTRAINT creneau_modele_pkey PRIMARY KEY (id),
    CONSTRAINT creneau_modele_modele_fkey FOREIGN KEY (modele_id)      REFERENCES modele_semaine(id) ON DELETE CASCADE,
    CONSTRAINT creneau_modele_type_fkey   FOREIGN KEY (type_seance_id) REFERENCES type_seance(id),
    CONSTRAINT creneau_modele_jour_chk    CHECK (jour_semaine BETWEEN 1 AND 7)
);
CREATE INDEX idx_creneau_modele_modele ON creneau_modele (modele_id);
