-- ============================================================
-- V82 — Objectif hebdomadaire de charge, par équipe
--
-- Le préparateur fixe un objectif de distance hebdomadaire (km/joueur) pour son
-- équipe ; il est RECONDUIT chaque semaine (une seule valeur courante par équipe,
-- modifiable via l'écran Charge). La « suggestion intelligente » (charge cible A.5)
-- reste calculée à la volée côté Python et n'est PAS stockée.
--
-- Écriture gardée par une nouvelle permission `predictions:write` (seul domaine
-- qui n'avait pas de write) — seedée aux rôles qui pilotent la charge.
-- ============================================================
SET search_path = public;

CREATE TABLE objectif_hebdo (
    id                  uuid DEFAULT uuid_generate_v4() NOT NULL,
    equipe_id           uuid NOT NULL,
    objectif_distance_m integer,
    updated_by          uuid,
    updated_at          timestamp DEFAULT now() NOT NULL,
    CONSTRAINT objectif_hebdo_pkey PRIMARY KEY (id),
    CONSTRAINT objectif_hebdo_equipe_fkey FOREIGN KEY (equipe_id) REFERENCES equipe(id) ON DELETE CASCADE,
    CONSTRAINT objectif_hebdo_equipe_unique UNIQUE (equipe_id),
    CONSTRAINT objectif_hebdo_distance_check CHECK (objectif_distance_m IS NULL OR objectif_distance_m >= 0)
);

-- ── Nouvelle permission predictions:write pour les rôles qui pilotent la charge ──
-- PRÉSIDENT (admin club, toutes écritures), ENTRAÎNEUR, PRÉPARATEUR, ENTRAÎNEUR EN CHEF.
-- Médical / Administratif : exclus. SUPER_ADMIN a toutes les permissions d'office (hors RBAC).
INSERT INTO role_permission (role_id, permission)
SELECT r.id, 'predictions:write'
FROM (VALUES
   ('a0000000-0000-0000-0000-000000000001'::uuid),   -- PRESIDENT
   ('a0000000-0000-0000-0000-000000000002'::uuid),   -- ENTRAINEUR
   ('a0000000-0000-0000-0000-000000000003'::uuid),   -- PREPARATEUR
   ('a0000000-0000-0000-0000-000000000006'::uuid)    -- ENTRAINEUR_CHEF
) AS r(id)
ON CONFLICT DO NOTHING;
