-- ============================================================
-- V55 — Cohérence de l'échelle commerciale : rattachement de 2 features à des packs
--
-- Deux features « flottaient » hors des packs et étaient donc soit offertes par effet de bord,
-- soit inaccessibles aux nouveaux clubs :
--
--   1. Bibliothèque de séances-modèles : jusqu'ici gardée par `coaching:access` (ancré au socle
--      côté Java) → de fait offerte dès Essentiel. On lui donne un module DÉDIÉ `seances_modeles`
--      (Java : FeatureModule.SEANCES_MODELES) et une permission dédiée `seances_modeles:access`.
--      Le module est ajouté aux packs Prépa / Performance / Complet (palier « Prépa+ »). La
--      permission est semée aux mêmes rôles que coaching:access (Président 001, Entraîneur 002,
--      Entraîneur en chef 006) → accès inchangé pour eux, mais coupé (403) si le module est off.
--
--   2. Licences & documents (`documents_admin`) : n'était dans AUCUN pack (module orphelin activé
--      seulement via surcharges club_module manuelles) → les nouveaux clubs, même en Complet, ne
--      l'avaient pas. On l'ajoute aux 4 packs (dès Essentiel).
--
-- Résolution live (ClubModulesService = modules(pack) ∪ surcharges) → s'applique immédiatement
-- aux clubs existants ET futurs. Aucune donnée métier touchée. Migration 100 % idempotente.
-- ============================================================
SET search_path = public;

-- 1a) Module `seances_modeles` → packs Prépa, Performance, Complet (palier Prépa+)
INSERT INTO pack_module (pack_code, module_code) VALUES
 ('prepa','seances_modeles'),
 ('performance','seances_modeles'),
 ('complet','seances_modeles')
ON CONFLICT (pack_code, module_code) DO NOTHING;

-- 1b) Permission dédiée `seances_modeles:access` → mêmes rôles que coaching:access
--     (Président 001, Entraîneur 002, Entraîneur en chef 006). SUPER_ADMIN bypasse (god mode).
INSERT INTO role_permission (role_id, permission) VALUES
 ('a0000000-0000-0000-0000-000000000001','seances_modeles:access'),
 ('a0000000-0000-0000-0000-000000000002','seances_modeles:access'),
 ('a0000000-0000-0000-0000-000000000006','seances_modeles:access')
ON CONFLICT (role_id, permission) DO NOTHING;

-- 2) Module `documents_admin` → les 4 packs (dès Essentiel)
INSERT INTO pack_module (pack_code, module_code) VALUES
 ('essentiel','documents_admin'),
 ('prepa','documents_admin'),
 ('performance','documents_admin'),
 ('complet','documents_admin')
ON CONFLICT (pack_code, module_code) DO NOTHING;

-- Nettoyage : les surcharges club_module MANUEL_ON de documents_admin deviennent redondantes
-- (le pack le fournit désormais). On les retire pour que le module « suive le pack » proprement
-- (source PACK au lieu de MANUEL_ON dans l'écran d'abonnement). Un club qui l'avait garde l'accès.
DELETE FROM club_module WHERE module_code = 'documents_admin' AND actif = TRUE;
