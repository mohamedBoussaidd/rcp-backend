-- ============================================================
-- V30 — RBAC data-driven (permissions, rôles, affectations)
--
-- Pose la fondation : les droits ne sont plus codés en dur mais deviennent de la donnée.
--   app_role         : rôle = paquet nommé de permissions (système si club_id NULL, sinon custom)
--   role_permission  : permissions accordées à un rôle (code = chaîne d'autorité, ex. seances:write)
--   affectation_role : user ↔ rôle, scopé équipe (equipe_id) ou club entier (equipe_id NULL)
--
-- Dual-run : la colonne utilisateur.role (enum 1 rôle) est CONSERVÉE en parallèle. SUPER_ADMIN
-- (bypass) et JOUEUR (self-scope /api/moi) restent gérés via cette colonne, pas via le RBAC.
--
-- Seed iso de l'existant (SecurityConfig) + décision produit : le PRÉSIDENT devient admin club
-- complet (tous les droits d'écriture de son club).
-- ============================================================
SET search_path = public;

-- ── Rôle applicatif (bundle de permissions) ────────────────────────────────
CREATE TABLE app_role (
    id          uuid DEFAULT uuid_generate_v4() NOT NULL,
    club_id     uuid,                          -- NULL = rôle système (commun à tous)
    code        varchar(50)  NOT NULL,
    libelle     varchar(80)  NOT NULL,
    systeme     boolean      NOT NULL DEFAULT false,
    created_at  timestamp    DEFAULT now() NOT NULL,
    CONSTRAINT app_role_pkey PRIMARY KEY (id),
    CONSTRAINT app_role_club_fkey FOREIGN KEY (club_id) REFERENCES club(id) ON DELETE CASCADE
);
-- code unique par club ; les rôles système (club_id NULL) partagent un même espace de noms
CREATE UNIQUE INDEX uq_app_role_code
    ON app_role (COALESCE(club_id, '00000000-0000-0000-0000-000000000000'::uuid), code);

-- ── Permissions d'un rôle ──────────────────────────────────────────────────
CREATE TABLE role_permission (
    role_id     uuid         NOT NULL,
    permission  varchar(50)  NOT NULL,         -- code (= chaîne d'autorité), ex. seances:write
    CONSTRAINT role_permission_pkey PRIMARY KEY (role_id, permission),
    CONSTRAINT role_permission_role_fkey FOREIGN KEY (role_id) REFERENCES app_role(id) ON DELETE CASCADE
);

-- ── Affectation user ↔ rôle (scopée équipe ou club) ────────────────────────
CREATE TABLE affectation_role (
    id          uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id     uuid         NOT NULL,
    club_id     uuid         NOT NULL,         -- club de l'affectation (scope club-wide)
    equipe_id   uuid,                          -- NULL = toutes les équipes du club
    role_id     uuid         NOT NULL,
    created_at  timestamp    DEFAULT now() NOT NULL,
    CONSTRAINT affectation_role_pkey PRIMARY KEY (id),
    CONSTRAINT affectation_role_user_fkey   FOREIGN KEY (user_id)   REFERENCES utilisateur(id) ON DELETE CASCADE,
    CONSTRAINT affectation_role_club_fkey   FOREIGN KEY (club_id)   REFERENCES club(id)        ON DELETE CASCADE,
    CONSTRAINT affectation_role_equipe_fkey FOREIGN KEY (equipe_id) REFERENCES equipe(id)      ON DELETE CASCADE,
    CONSTRAINT affectation_role_role_fkey   FOREIGN KEY (role_id)   REFERENCES app_role(id)    ON DELETE CASCADE
);
CREATE INDEX idx_affectation_user ON affectation_role (user_id);
CREATE UNIQUE INDEX uq_affectation
    ON affectation_role (user_id, role_id, COALESCE(equipe_id, '00000000-0000-0000-0000-000000000000'::uuid));

-- ============================================================
-- Seed des rôles système (UUID fixes pour référence stable)
-- ============================================================
INSERT INTO app_role (id, club_id, code, libelle, systeme) VALUES
 ('a0000000-0000-0000-0000-000000000001', NULL, 'PRESIDENT',     'Président',      true),
 ('a0000000-0000-0000-0000-000000000002', NULL, 'ENTRAINEUR',    'Entraîneur',     true),
 ('a0000000-0000-0000-0000-000000000003', NULL, 'PREPARATEUR',   'Préparateur',    true),
 ('a0000000-0000-0000-0000-000000000004', NULL, 'MEDICAL',       'Staff médical',  true),
 ('a0000000-0000-0000-0000-000000000005', NULL, 'ADMINISTRATIF', 'Administratif',  true);

-- ── Lectures + config notifications : communes aux 4 rôles staff ───────────
INSERT INTO role_permission (role_id, permission)
SELECT r.id, p.perm
FROM (VALUES
   ('a0000000-0000-0000-0000-000000000001'::uuid),   -- PRESIDENT
   ('a0000000-0000-0000-0000-000000000002'::uuid),   -- ENTRAINEUR
   ('a0000000-0000-0000-0000-000000000003'::uuid),   -- PREPARATEUR
   ('a0000000-0000-0000-0000-000000000004'::uuid)    -- MEDICAL
) AS r(id)
CROSS JOIN (VALUES
   ('seances:read'),('predictions:read'),('joueurs:read'),('pesees:read'),
   ('blessures:read'),('documents:read'),('wellness:read'),('conseils:read'),
   ('exercices:read'),('formations:read'),('schemas:read'),('plandejeu:read'),
   ('matchs:read'),('configuration:read'),('notifications:config')
) AS p(perm);

-- ── PRÉSIDENT : admin club complet (TOUTES les écritures + gestion club) ───
INSERT INTO role_permission (role_id, permission) VALUES
 ('a0000000-0000-0000-0000-000000000001','seances:write'),
 ('a0000000-0000-0000-0000-000000000001','presence:write'),
 ('a0000000-0000-0000-0000-000000000001','typeseances:write'),
 ('a0000000-0000-0000-0000-000000000001','joueurs:write'),
 ('a0000000-0000-0000-0000-000000000001','pesees:write'),
 ('a0000000-0000-0000-0000-000000000001','gps:import'),
 ('a0000000-0000-0000-0000-000000000001','blessures:write'),
 ('a0000000-0000-0000-0000-000000000001','documents:write'),
 ('a0000000-0000-0000-0000-000000000001','wellness:treat'),
 ('a0000000-0000-0000-0000-000000000001','wellness:reopen'),
 ('a0000000-0000-0000-0000-000000000001','conseils:write'),
 ('a0000000-0000-0000-0000-000000000001','exercices:write'),
 ('a0000000-0000-0000-0000-000000000001','formations:write'),
 ('a0000000-0000-0000-0000-000000000001','schemas:write'),
 ('a0000000-0000-0000-0000-000000000001','plandejeu:write'),
 ('a0000000-0000-0000-0000-000000000001','matchs:write'),
 ('a0000000-0000-0000-0000-000000000001','configuration:write'),
 ('a0000000-0000-0000-0000-000000000001','club:manage');

-- ── ENTRAÎNEUR : séances + tactique ───────────────────────────────────────
INSERT INTO role_permission (role_id, permission) VALUES
 ('a0000000-0000-0000-0000-000000000002','seances:write'),
 ('a0000000-0000-0000-0000-000000000002','presence:write'),
 ('a0000000-0000-0000-0000-000000000002','typeseances:write'),
 ('a0000000-0000-0000-0000-000000000002','joueurs:write'),
 ('a0000000-0000-0000-0000-000000000002','exercices:write'),
 ('a0000000-0000-0000-0000-000000000002','formations:write'),
 ('a0000000-0000-0000-0000-000000000002','schemas:write'),
 ('a0000000-0000-0000-0000-000000000002','plandejeu:write'),
 ('a0000000-0000-0000-0000-000000000002','matchs:write');

-- ── PRÉPARATEUR : séances physiques + GPS + pesées + suivi ────────────────
INSERT INTO role_permission (role_id, permission) VALUES
 ('a0000000-0000-0000-0000-000000000003','seances:write'),
 ('a0000000-0000-0000-0000-000000000003','presence:write'),
 ('a0000000-0000-0000-0000-000000000003','typeseances:write'),
 ('a0000000-0000-0000-0000-000000000003','joueurs:write'),
 ('a0000000-0000-0000-0000-000000000003','pesees:write'),
 ('a0000000-0000-0000-0000-000000000003','gps:import'),
 ('a0000000-0000-0000-0000-000000000003','wellness:treat'),
 ('a0000000-0000-0000-0000-000000000003','conseils:write'),
 ('a0000000-0000-0000-0000-000000000003','configuration:write');

-- ── MÉDICAL : blessures + documents + gênes + conseils ────────────────────
INSERT INTO role_permission (role_id, permission) VALUES
 ('a0000000-0000-0000-0000-000000000004','blessures:write'),
 ('a0000000-0000-0000-0000-000000000004','documents:write'),
 ('a0000000-0000-0000-0000-000000000004','wellness:treat'),
 ('a0000000-0000-0000-0000-000000000004','wellness:reopen'),
 ('a0000000-0000-0000-0000-000000000004','conseils:write');

-- ADMINISTRATIF : aucune permission (réservé à un usage futur), rôle créé pour affectation.

-- ============================================================
-- Migration des utilisateurs existants → affectations (iso rôle enum actuel)
-- SUPER_ADMIN (bypass) et JOUEUR (self-scope) : aucune affectation.
-- ============================================================

-- Président : affectation club-wide (equipe_id NULL)
INSERT INTO affectation_role (user_id, club_id, equipe_id, role_id)
SELECT u.id, u.club_id, NULL, 'a0000000-0000-0000-0000-000000000001'::uuid
FROM utilisateur u
WHERE u.role = 'PRESIDENT' AND u.club_id IS NOT NULL;

-- Staff d'équipe : affectation scopée à leur équipe (club déduit de l'équipe)
INSERT INTO affectation_role (user_id, club_id, equipe_id, role_id)
SELECT u.id, e.club_id, u.equipe_id,
   CASE u.role
     WHEN 'ENTRAINEUR'    THEN 'a0000000-0000-0000-0000-000000000002'::uuid
     WHEN 'PREPARATEUR'   THEN 'a0000000-0000-0000-0000-000000000003'::uuid
     WHEN 'MEDICAL'       THEN 'a0000000-0000-0000-0000-000000000004'::uuid
     WHEN 'ADMINISTRATIF' THEN 'a0000000-0000-0000-0000-000000000005'::uuid
   END
FROM utilisateur u
JOIN equipe e ON e.id = u.equipe_id
WHERE u.role IN ('ENTRAINEUR','PREPARATEUR','MEDICAL','ADMINISTRATIF')
  AND u.equipe_id IS NOT NULL;
