-- ============================================================
-- V54 — Bibliothèque de séances-modèles (espace Coaching)
--
-- Une séance-modèle est un gabarit de séance RÉUTILISABLE : contenu (type, objectif, durée,
-- objectifs de volume, exercices ordonnés) SANS ancrage temporel. On l'instancie (« Planifier »)
-- pour créer une vraie séance dans le calendrier. Miroir volontaire de seance / seance_exercice,
-- scope CLUB + règle « créateur-only », comme la bibliothèque d'exercices.
-- ============================================================
SET search_path = public;

CREATE TABLE seance_modele (
    id                                   uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    club_id                              uuid NOT NULL REFERENCES club(id) ON DELETE CASCADE,
    nom                                  varchar(160) NOT NULL,
    type_seance_id                       uuid NOT NULL REFERENCES type_seance(id),
    objectif                             text,
    duree_minutes                        smallint,
    objectif_distance_m                  integer,
    objectif_intensite                   smallint,
    objectif_distance_haute_intensite_m  integer,
    description                          text,
    cree_par                             uuid,
    equipe_origine_id                    uuid,
    created_at                           timestamp NOT NULL DEFAULT now()
);

CREATE INDEX idx_seance_modele_club ON seance_modele(club_id);

CREATE TABLE seance_modele_exercice (
    id                          uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    seance_modele_id            uuid NOT NULL REFERENCES seance_modele(id) ON DELETE CASCADE,
    exercice_id                 uuid NOT NULL,
    ordre                       smallint NOT NULL DEFAULT 0,
    duree_minutes               smallint,
    intensite                   smallint,
    distance_attendue_m         integer,
    distance_haute_intensite_m  integer,
    nb_sprints                  smallint
);

CREATE INDEX idx_seance_modele_exercice_modele ON seance_modele_exercice(seance_modele_id);
