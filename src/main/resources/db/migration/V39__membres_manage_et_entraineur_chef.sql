-- ============================================================
-- V39 — Gestion des membres scopée + modèle « Entraîneur en chef »
--
-- Problème corrigé : la gestion des comptes était ouverte en bloc à tout entraîneur/
-- préparateur du club (un entraîneur réserve pouvait supprimer l'entraîneur d'une autre
-- équipe). On introduit une permission dédiée `membres:manage` SCOPÉE par l'affectation
-- (équipe ou club) + un modèle de rôle « Entraîneur en chef » (admin club côté staff).
--
--   membres:manage  : gérer les comptes (staff & joueurs) DE SON PÉRIMÈTRE.
--     - Président  : club entier (déjà admin club).
--     - Entraîneur : sa seule équipe (affectation scopée équipe).
--   club:manage     : reste l'admin club complet (équipes + rôles + tous les membres).
--
-- La hiérarchie (un acteur ne gère qu'un membre de rang STRICTEMENT inférieur, dans son
-- périmètre) est appliquée côté service (GestionClubService), pas en base.
-- ============================================================
SET search_path = public;

-- ── membres:manage pour les rôles système existants ────────────────────────
INSERT INTO role_permission (role_id, permission) VALUES
 ('a0000000-0000-0000-0000-000000000001', 'membres:manage'),   -- PRÉSIDENT (club-wide via son affectation)
 ('a0000000-0000-0000-0000-000000000002', 'membres:manage')    -- ENTRAÎNEUR (équipe via son affectation)
ON CONFLICT DO NOTHING;

-- ── Nouveau rôle système « Entraîneur en chef » ────────────────────────────
-- À affecter CLUB-WIDE (equipe_id NULL) via la matrice Rôles & accès : pouvoirs
-- entraîneur + admin club (membres, équipes, rôles) sur tout le club.
INSERT INTO app_role (id, club_id, code, libelle, systeme) VALUES
 ('a0000000-0000-0000-0000-000000000006', NULL, 'ENTRAINEUR_CHEF', 'Entraîneur en chef', true)
ON CONFLICT DO NOTHING;

-- Lectures + config (même socle que les autres rôles staff)
INSERT INTO role_permission (role_id, permission)
SELECT 'a0000000-0000-0000-0000-000000000006'::uuid, p.perm
FROM (VALUES
   ('seances:read'),('predictions:read'),('joueurs:read'),('pesees:read'),
   ('blessures:read'),('documents:read'),('wellness:read'),('conseils:read'),
   ('exercices:read'),('formations:read'),('schemas:read'),('plandejeu:read'),
   ('matchs:read'),('configuration:read'),('notifications:config')
) AS p(perm)
ON CONFLICT DO NOTHING;

-- Écritures entraîneur + gestion du club
INSERT INTO role_permission (role_id, permission)
SELECT 'a0000000-0000-0000-0000-000000000006'::uuid, p.perm
FROM (VALUES
   ('seances:write'),('presence:write'),('typeseances:write'),('joueurs:write'),
   ('exercices:write'),('formations:write'),('schemas:write'),('plandejeu:write'),
   ('matchs:write'),('membres:manage'),('club:manage')
) AS p(perm)
ON CONFLICT DO NOTHING;
