-- ============================================================
-- V20 — Joueurs à surveiller pour un match (consigne de prépa)
-- Liste, par match, des joueurs à surveiller : soit un adversaire (nom libre,
-- l'effectif adverse n'est pas en base), soit un de nos joueurs (joueur_id).
-- ============================================================
SET search_path = public;

CREATE TABLE match_joueur_surveille (
    id          uuid DEFAULT uuid_generate_v4() NOT NULL,
    match_id    uuid NOT NULL,
    cible       varchar(10) NOT NULL DEFAULT 'ADVERSE',  -- ADVERSE | EQUIPE
    joueur_id   uuid,            -- renseigné si cible = EQUIPE (un de nos joueurs)
    nom         varchar(120),    -- nom libre (adversaire) ou libellé d'affichage
    note        text,
    created_at  timestamp DEFAULT now() NOT NULL,
    CONSTRAINT match_joueur_surveille_pkey PRIMARY KEY (id),
    CONSTRAINT match_js_match_fkey  FOREIGN KEY (match_id)  REFERENCES match_prepa(id) ON DELETE CASCADE,
    CONSTRAINT match_js_joueur_fkey FOREIGN KEY (joueur_id) REFERENCES joueur(id)      ON DELETE SET NULL
);
CREATE INDEX idx_match_js_match ON match_joueur_surveille (match_id);
