-- ============================================================
-- V92 — Événements extrasportifs
--
-- Réunion, déplacement, vie de club, examens scolaires, indisponibilité… Ces
-- événements structurent la semaine d'un club amateur et expliquent la moitié
-- des absences, mais n'avaient aucune place dans l'application.
--
-- POURQUOI UNE TABLE DÉDIÉE, et pas un type de séance de plus :
--   · `type_seance` est GLOBAL (aucun club_id) — un type « Réunion » ajouté pour
--     un club apparaîtrait chez tous les autres ;
--   · une `seance` est le support de la charge, du GPS, de la présence et du RPE.
--     Un repas d'équipe qui compte dans l'ACWR n'aurait aucun sens.
--
-- Rangé dans le module SOCLE « Planning » : poser un événement au calendrier est
-- une fonction de base, la gater derrière un abonnement serait incohérent.
-- L'écriture est gouvernée par la permission existante `seances:write`.
--
-- Ciblage : au niveau ÉQUIPE par défaut, et facultativement sur des personnes
-- précises via evenement_joueur (« Martin a ses examens jeudi »). Aucune ligne
-- dans evenement_joueur = l'événement concerne toute l'équipe.
-- ============================================================
SET search_path = public;

CREATE TABLE evenement (
    id            uuid DEFAULT uuid_generate_v4() NOT NULL,
    club_id       uuid NOT NULL,
    equipe_id     uuid,
    type          varchar(30)  NOT NULL,
    titre         varchar(160) NOT NULL,
    date          date         NOT NULL,
    date_fin      date,
    heure_debut   time without time zone,
    heure_fin     time without time zone,
    lieu          varchar(160),
    description   text,
    /** L'événement est-il visible des joueurs concernés dans leur espace mobile ? */
    visible_joueurs boolean NOT NULL DEFAULT true,
    cree_par      uuid,
    created_at    timestamp without time zone NOT NULL DEFAULT now(),
    CONSTRAINT evenement_pkey PRIMARY KEY (id),
    CONSTRAINT evenement_club_fkey   FOREIGN KEY (club_id)   REFERENCES club(id)        ON DELETE CASCADE,
    CONSTRAINT evenement_equipe_fkey FOREIGN KEY (equipe_id) REFERENCES equipe(id)      ON DELETE CASCADE,
    CONSTRAINT evenement_auteur_fkey FOREIGN KEY (cree_par)  REFERENCES utilisateur(id) ON DELETE SET NULL,
    CONSTRAINT evenement_type_chk CHECK (type IN (
        'VIE_CLUB', 'DEPLACEMENT', 'SCOLAIRE', 'CONVIVIALITE', 'RENDEZ_VOUS', 'INDISPONIBILITE', 'AUTRE')),
    -- Une plage qui se termine avant de commencer passait sans bruit et cassait l'affichage.
    CONSTRAINT evenement_periode_chk CHECK (date_fin IS NULL OR date_fin >= date)
);

CREATE INDEX idx_evenement_club_date ON evenement (club_id, date);
CREATE INDEX idx_evenement_equipe    ON evenement (equipe_id);

-- Personnes nommément concernées. La table `joueur` porte joueurs ET staff depuis la
-- refonte « fiche unifiée » : une seule FK suffit pour cibler les deux.
CREATE TABLE evenement_joueur (
    evenement_id uuid NOT NULL,
    joueur_id    uuid NOT NULL,
    CONSTRAINT evenement_joueur_pkey PRIMARY KEY (evenement_id, joueur_id),
    CONSTRAINT ej_evenement_fkey FOREIGN KEY (evenement_id) REFERENCES evenement(id) ON DELETE CASCADE,
    CONSTRAINT ej_joueur_fkey    FOREIGN KEY (joueur_id)    REFERENCES joueur(id)    ON DELETE CASCADE
);

CREATE INDEX idx_evenement_joueur_joueur ON evenement_joueur (joueur_id);
