-- ============================================================
-- V31 — Match v2 : partage joueur, logistique, compo enrichie
--
-- 1. match_prepa : publication vers les joueurs (publie / publie_at), réglage
--    de visibilité de la compo (compo_visible) et infos logistiques de match.
-- 2. match_compo : consigne individuelle par joueur (affichée au joueur concerné).
-- 3. match_suspendu : joueurs suspendus pour CE match (case manuelle du staff),
--    exclus de l'auto-compo au même titre que les blessés (module médical).
-- ============================================================
SET search_path = public;

-- ── Publication, visibilité compo et logistique ────────────────────────────
ALTER TABLE match_prepa
    ADD COLUMN publie              boolean   NOT NULL DEFAULT false,
    ADD COLUMN publie_at           timestamp,
    ADD COLUMN compo_visible       boolean   NOT NULL DEFAULT true,
    ADD COLUMN lieu_rdv            varchar(160),
    ADD COLUMN heure_rdv           time,
    ADD COLUMN couleur_maillot     varchar(60),
    ADD COLUMN infos_logistiques   text;

-- ── Consigne individuelle par joueur dans la compo ─────────────────────────
ALTER TABLE match_compo
    ADD COLUMN consigne text;

-- ── Joueurs suspendus pour un match (indisponibilité manuelle) ─────────────
CREATE TABLE match_suspendu (
    id          uuid DEFAULT uuid_generate_v4() NOT NULL,
    match_id    uuid NOT NULL,
    joueur_id   uuid NOT NULL,
    created_at  timestamp DEFAULT now() NOT NULL,
    CONSTRAINT match_suspendu_pkey PRIMARY KEY (id),
    CONSTRAINT match_suspendu_match_fkey FOREIGN KEY (match_id) REFERENCES match_prepa(id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX uq_match_suspendu ON match_suspendu (match_id, joueur_id);
