-- ============================================================
-- V53 — Navigation par zones : recalage des permissions sur les 7 menus
--
-- Contexte : la nav est désormais « par zones » (Planning / Coaching / Performance / Suivi des
-- membres / Médical / Administration / Gestion du club) mais le gating restait « par rôle/pack »,
-- d'où des fuites entre zones. Cette migration recale trois points :
--
--   1. Nouvelle clé `coaching:access` : gate du menu Coaching (zone tactique/terrain). Attribuée
--      aux rôles qui pilotent le terrain — Président (001), Entraîneur (002), Entraîneur en chef
--      (006) — mais PAS au Préparateur (003) : il sort proprement de Coaching (fin du leak, où il
--      entrait via `seances:write` partagé). Clé rattachée à un module socle côté Java
--      (FeatureModule.modulesDe) → survit toujours au filtrage pack.
--
--   2. Planning lisible par l'Administratif (005) : il gère licences/documents et doit voir le
--      calendrier du club → `seances:read` (lecture seule ; il n'a pas `seances:write`).
--
--   3. Suivi individuel (Entretiens + Axes) recentré sur Président (001), Entraîneurs (002 + chef
--      006) et Administratif (005). On RETIRE le Préparateur (003) et le Médical (004).
--
-- Rôles système (club_id NULL) partagés par tous les clubs → s'applique aux clubs existants ET
-- futurs sans retoucher le seeder. Aucune donnée métier touchée.
-- ============================================================
SET search_path = public;

-- 1) coaching:access → Président, Entraîneur, Entraîneur en chef
INSERT INTO role_permission (role_id, permission) VALUES
 ('a0000000-0000-0000-0000-000000000001','coaching:access'),
 ('a0000000-0000-0000-0000-000000000002','coaching:access'),
 ('a0000000-0000-0000-0000-000000000006','coaching:access')
ON CONFLICT (role_id, permission) DO NOTHING;

-- 2) seances:read → Administratif (Planning en lecture)
INSERT INTO role_permission (role_id, permission) VALUES
 ('a0000000-0000-0000-0000-000000000005','seances:read')
ON CONFLICT (role_id, permission) DO NOTHING;

-- 3a) Suivi individuel : ajout de l'Administratif (lecture + conduite d'entretiens)
INSERT INTO role_permission (role_id, permission) VALUES
 ('a0000000-0000-0000-0000-000000000005','entretien:read'),
 ('a0000000-0000-0000-0000-000000000005','entretien:write'),
 ('a0000000-0000-0000-0000-000000000005','axe:read'),
 ('a0000000-0000-0000-0000-000000000005','axe:write')
ON CONFLICT (role_id, permission) DO NOTHING;

-- 3b) Retrait du Préparateur (003) du Suivi individuel
DELETE FROM role_permission
 WHERE role_id = 'a0000000-0000-0000-0000-000000000003'
   AND permission IN ('entretien:read','entretien:write','axe:read','axe:write');

-- 3c) Retrait du Médical (004) : ne doit plus voir les Entretiens
DELETE FROM role_permission
 WHERE role_id = 'a0000000-0000-0000-0000-000000000004'
   AND permission IN ('entretien:read','axe:read');
