-- ============================================================
-- V98 — Relation entraîneur ↔ joueur : objectifs individuels, notes de staff, fil de vie
--
-- Trois manques que l'entraîneur comblait jusqu'ici de mémoire ou sur un carnet :
--   · `objectif_joueur` : 2-3 objectifs datés, revus en entretien ;
--   · `note_joueur`     : observation horodatée du staff sportif ;
--   · le fil de vie n'a AUCUNE table — c'est une agrégation à la lecture de ce qui existe déjà
--     (blessures, matchs, entretiens, objectifs, notes). Rien à stocker, rien à désynchroniser.
--
-- Rattachés au module EXISTANT `suivi_individuel` (entretiens, axes de travail) : même métier,
-- donc aucune ligne `pack_module` à ajouter et aucun nouvel add-on à activer.
--
-- Sur les notes : elles concernent une personne identifiée et sont réservées au staff sportif.
-- Elles ne sont jamais exposées au joueur dans la PWA, et le champ reste factuel par convention
-- d'usage — la même règle que les consignes médicales, qui ne remontent pas non plus côté joueur.
-- ============================================================
SET search_path = public;

CREATE TABLE IF NOT EXISTS objectif_joueur (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    joueur_id    uuid NOT NULL REFERENCES joueur (id) ON DELETE CASCADE,
    titre        varchar(160) NOT NULL,
    description  text,
    echeance     date,
    statut       varchar(20) NOT NULL DEFAULT 'EN_COURS',
    cree_par     uuid,
    created_at   timestamp NOT NULL DEFAULT now(),
    updated_at   timestamp NOT NULL DEFAULT now(),
    CONSTRAINT objectif_statut_chk CHECK (statut IN ('EN_COURS', 'ATTEINT', 'ABANDONNE'))
);

CREATE INDEX IF NOT EXISTS idx_objectif_joueur ON objectif_joueur (joueur_id, statut);

CREATE TABLE IF NOT EXISTS note_joueur (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    joueur_id    uuid NOT NULL REFERENCES joueur (id) ON DELETE CASCADE,
    texte        text NOT NULL,
    date_note    date NOT NULL DEFAULT CURRENT_DATE,
    auteur_id    uuid,
    created_at   timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_note_joueur ON note_joueur (joueur_id, date_note DESC);

COMMENT ON TABLE note_joueur IS
    'Observations du staff sportif sur un joueur. Jamais exposées au joueur (PWA).';

-- ── Permissions ──
-- Lecture aux rôles qui suivent les joueurs au quotidien ; écriture à ceux qui les encadrent.
-- SUPER_ADMIN a toutes les permissions d'office (hors RBAC).
INSERT INTO role_permission (role_id, permission)
SELECT r.id, 'suivi_coach:read'
FROM (VALUES
   ('a0000000-0000-0000-0000-000000000001'::uuid),   -- PRESIDENT
   ('a0000000-0000-0000-0000-000000000002'::uuid),   -- ENTRAINEUR
   ('a0000000-0000-0000-0000-000000000003'::uuid),   -- PREPARATEUR
   ('a0000000-0000-0000-0000-000000000006'::uuid)    -- ENTRAINEUR_CHEF
) AS r(id)
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission)
SELECT r.id, 'suivi_coach:write'
FROM (VALUES
   ('a0000000-0000-0000-0000-000000000002'::uuid),   -- ENTRAINEUR
   ('a0000000-0000-0000-0000-000000000006'::uuid)    -- ENTRAINEUR_CHEF
) AS r(id)
ON CONFLICT DO NOTHING;
