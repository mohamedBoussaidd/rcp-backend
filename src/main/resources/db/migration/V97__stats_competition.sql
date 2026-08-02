-- ============================================================
-- V97 — Feuille de match & statistiques individuelles (module `stats_competition`)
--
-- Ce que le coach réclamait — temps de jeu, présences en match, buts, cartons — n'existait
-- nulle part : `match_compo` ne portait qu'un statut (TITULAIRE / REMPLACANT / …), sans la
-- moindre minute. On l'enrichit d'une feuille de match par joueur.
--
-- TROIS SOURCES DE TEMPS DE JEU, jamais fusionnées en base :
--   · saisie   → `minute_entree` / `minute_sortie` renseignées par le staff après le match ;
--   · GPS      → NON STOCKÉ : dérivé à la lecture de `donnee_gps.duree_minutes` sur la séance
--                liée au match (`match_prepa.session_gps_id`). Le figer ici produirait un temps
--                faux le jour où l'on change la séance liée ;
--   · fédération → `temps_jeu_federation`, colonne d'accueil pour le futur connecteur.
-- La valeur qui fait foi dans les totaux est résolue côté service : saisie > fédération > GPS.
-- Le GPS mesure un temps de PORT de capteur, pas des minutes jouées : un remplaçant qui
-- s'échauffe 40 min en porterait 40. Il n'est donc retenu que pour les joueurs déclarés
-- entrés en jeu (cf. `entre_en_jeu`).
--
-- Module ADD-ON `stats_competition` : dans AUCUN pack, activé club par club par le
-- super-admin (comme `import_photo_ia` / `assistant_briefing`) → aucune ligne `pack_module`.
-- Les permissions sont semées aux rôles qui vivent la compétition ; la visibilité reste
-- conditionnée à l'activation du module (résolution live pack ∪ overrides).
-- ============================================================
SET search_path = public;

-- ── Feuille de match, par joueur et par match ──
ALTER TABLE match_compo
    ADD COLUMN IF NOT EXISTS entre_en_jeu          boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS minute_entree         smallint,
    ADD COLUMN IF NOT EXISTS minute_sortie         smallint,
    ADD COLUMN IF NOT EXISTS temps_jeu_federation  smallint,
    ADD COLUMN IF NOT EXISTS buts                  smallint NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS passes_decisives      smallint NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS cartons_jaunes        smallint NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS carton_rouge          boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS clean_sheet           boolean NOT NULL DEFAULT false;

-- Un titulaire est entré en jeu par définition : sans ce rattrapage, tout l'historique
-- existant serait compté comme « jamais entré » et le temps de jeu GPS ne serait jamais retenu.
UPDATE match_compo SET entre_en_jeu = true WHERE statut = 'TITULAIRE';

-- Garde-fous : des minutes hors bornes fausseraient tous les cumuls sans jamais lever d'erreur.
ALTER TABLE match_compo
    ADD CONSTRAINT match_compo_minute_entree_chk
        CHECK (minute_entree IS NULL OR (minute_entree >= 0 AND minute_entree <= 130)),
    ADD CONSTRAINT match_compo_minute_sortie_chk
        CHECK (minute_sortie IS NULL OR (minute_sortie >= 0 AND minute_sortie <= 130)),
    ADD CONSTRAINT match_compo_minutes_ordre_chk
        CHECK (minute_entree IS NULL OR minute_sortie IS NULL OR minute_sortie >= minute_entree),
    ADD CONSTRAINT match_compo_tj_federation_chk
        CHECK (temps_jeu_federation IS NULL OR (temps_jeu_federation >= 0 AND temps_jeu_federation <= 130));

COMMENT ON COLUMN match_compo.entre_en_jeu IS
    'Le joueur a-t-il foulé la pelouse ? Titulaire = true ; remplaçant = true seulement s''il est entré.';
COMMENT ON COLUMN match_compo.temps_jeu_federation IS
    'Minutes jouées d''après la fédération (connecteur à venir). Le temps GPS, lui, est dérivé à la lecture.';

-- Agrégats par joueur sur une saison : on balaye la compo de tous les matchs d'une équipe.
CREATE INDEX IF NOT EXISTS idx_match_compo_joueur ON match_compo (joueur_id);

-- ── Permissions du module ──
-- Semées aux rôles qui composent l'équipe et suivent les joueurs. Le préparateur y a droit en
-- lecture : le croisement charge ⇄ temps de jeu est précisément son indicateur.
-- SUPER_ADMIN a toutes les permissions d'office (hors RBAC).
INSERT INTO role_permission (role_id, permission)
SELECT r.id, p.perm
FROM (VALUES
   ('a0000000-0000-0000-0000-000000000001'::uuid),   -- PRESIDENT
   ('a0000000-0000-0000-0000-000000000002'::uuid),   -- ENTRAINEUR
   ('a0000000-0000-0000-0000-000000000003'::uuid),   -- PREPARATEUR
   ('a0000000-0000-0000-0000-000000000006'::uuid)    -- ENTRAINEUR_CHEF
) AS r(id)
CROSS JOIN (VALUES ('stats:read')) AS p(perm)
ON CONFLICT DO NOTHING;

-- L'écriture de la feuille de match reste à ceux qui gèrent le match.
INSERT INTO role_permission (role_id, permission)
SELECT r.id, 'stats:write'
FROM (VALUES
   ('a0000000-0000-0000-0000-000000000002'::uuid),   -- ENTRAINEUR
   ('a0000000-0000-0000-0000-000000000006'::uuid)    -- ENTRAINEUR_CHEF
) AS r(id)
ON CONFLICT DO NOTHING;
