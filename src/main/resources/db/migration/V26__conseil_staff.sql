-- ============================================================
-- V26 — Conseils du staff (médical / préparateur) au joueur
--
-- conseil_staff : carte de conseil (titre + texte + icône) rédigée par le staff
--                 et affichée au joueur dans son suivi subjectif (wellness/sRPE).
--                 joueur_id NULL  -> conseil commun à toute l'équipe.
--                 joueur_id défini -> conseil personnel à ce joueur (en plus).
-- ============================================================
SET search_path = public;

CREATE TABLE conseil_staff (
    id          uuid DEFAULT uuid_generate_v4() NOT NULL,
    equipe_id   uuid NOT NULL,
    joueur_id   uuid,                       -- NULL = conseil d'équipe
    titre       varchar(120) NOT NULL,
    texte       text NOT NULL,
    icone       varchar(40),                -- clé d'icône front (ex. HYDRATATION, SOMMEIL, MOBILITE)
    cree_par    uuid,
    created_at  timestamp DEFAULT now() NOT NULL,
    updated_at  timestamp DEFAULT now() NOT NULL,
    CONSTRAINT conseil_staff_pkey PRIMARY KEY (id),
    CONSTRAINT conseil_staff_equipe_fkey FOREIGN KEY (equipe_id) REFERENCES equipe(id) ON DELETE CASCADE,
    CONSTRAINT conseil_staff_joueur_fkey FOREIGN KEY (joueur_id) REFERENCES joueur(id) ON DELETE CASCADE
);
CREATE INDEX idx_conseil_equipe ON conseil_staff (equipe_id);
CREATE INDEX idx_conseil_joueur ON conseil_staff (joueur_id);
