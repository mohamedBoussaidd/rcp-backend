-- V63 — Mode avancé sur les MODÈLES de séance (bibliothèque Coaching).
--
-- V61 avait enrichi la séance PLANIFIÉE (objectifs pédagogiques, dominantes, sous-principes,
-- blocs) mais pas son gabarit : on pouvait donc construire une séance riche au calendrier et
-- la reperdre entièrement en passant par la bibliothèque. Cette migration met le modèle au
-- même niveau, pour que `planifier` recopie le contenu avancé au lieu de le laisser tomber.
--
-- Volontairement ABSENT du modèle (et ce n'est pas un oubli) :
--   · duree_effective_minutes → ne se constate qu'après coup, sur une séance réalisée ;
--   · groupe_seance / groupe_seance_joueur → les groupes du jour désignent des JOUEURS réels
--     à une date donnée ; un gabarit n'a ni date ni effectif.

-- ── Objectifs pédagogiques (mêmes colonnes que seance) ───────────────────
ALTER TABLE seance_modele
    ADD COLUMN obj_tactique_org  text,
    ADD COLUMN obj_tactique_fonc text,
    ADD COLUMN obj_mental        text,
    ADD COLUMN obj_technique     text,
    ADD COLUMN obj_athletique    text;

-- ── Dominantes & sous-principes (référentiels V61 réutilisés tels quels) ──
CREATE TABLE seance_modele_dominante (
    seance_modele_id uuid NOT NULL REFERENCES seance_modele(id) ON DELETE CASCADE,
    dominante_id     uuid NOT NULL REFERENCES referentiel_dominante(id) ON DELETE CASCADE,
    PRIMARY KEY (seance_modele_id, dominante_id)
);

CREATE TABLE seance_modele_sous_principe (
    seance_modele_id uuid NOT NULL REFERENCES seance_modele(id) ON DELETE CASCADE,
    sous_principe_id uuid NOT NULL REFERENCES referentiel_sous_principe(id) ON DELETE CASCADE,
    PRIMARY KEY (seance_modele_id, sous_principe_id)
);

-- ── Blocs du gabarit (miroir de bloc_seance) ─────────────────────────────
CREATE TABLE bloc_seance_modele (
    id               uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    seance_modele_id uuid NOT NULL REFERENCES seance_modele(id) ON DELETE CASCADE,
    ordre            smallint NOT NULL DEFAULT 0,
    libelle          varchar(120) NOT NULL,
    sequencage       varchar(120),
    duree_minutes    smallint,
    zone_terrain     varchar(120)
);

CREATE INDEX idx_bloc_seance_modele_modele ON bloc_seance_modele(seance_modele_id);

-- Staff « par défaut » du bloc : recopié à la planification, ajustable ensuite sur la séance.
CREATE TABLE bloc_seance_modele_staff (
    bloc_id        uuid NOT NULL REFERENCES bloc_seance_modele(id) ON DELETE CASCADE,
    utilisateur_id uuid NOT NULL REFERENCES utilisateur(id) ON DELETE CASCADE,
    PRIMARY KEY (bloc_id, utilisateur_id)
);

-- Rattachement des lignes d'exercices du modèle à un bloc (NULL = liste plate, comportement actuel).
ALTER TABLE seance_modele_exercice
    ADD COLUMN bloc_id uuid REFERENCES bloc_seance_modele(id) ON DELETE SET NULL;
