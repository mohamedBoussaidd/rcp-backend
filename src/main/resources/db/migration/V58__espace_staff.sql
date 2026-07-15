-- ============================================================
-- V58 — Espace staff (application mobile)
--
-- Nouveau module activable `espace_staff` (cf. FeatureModule.ESPACE_STAFF) : zone /staff
-- mobile-first (agenda, appel, effectif, messagerie, documents, notifications push).
-- Double verrou : le club doit posséder le module (pack ou add-on club_module géré par le
-- super-admin), ET le rôle doit avoir la permission `espace_staff:access` (matrice Rôles & accès).
--
-- Packs : inclus dans Performance + Complet (décision user 2026-07-15) ; activable partout
-- en add-on via la surcharge club_module existante.
-- Permission seedée sur les 6 rôles système staff (club_id NULL → tous clubs, présents et futurs).
-- ============================================================
SET search_path = public;

INSERT INTO pack_module (pack_code, module_code) VALUES
 ('performance', 'espace_staff'),
 ('complet',     'espace_staff')
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission) VALUES
 ('a0000000-0000-0000-0000-000000000001', 'espace_staff:access'),  -- Président
 ('a0000000-0000-0000-0000-000000000002', 'espace_staff:access'),  -- Entraîneur
 ('a0000000-0000-0000-0000-000000000003', 'espace_staff:access'),  -- Préparateur
 ('a0000000-0000-0000-0000-000000000004', 'espace_staff:access'),  -- Staff médical
 ('a0000000-0000-0000-0000-000000000005', 'espace_staff:access'),  -- Administratif
 ('a0000000-0000-0000-0000-000000000006', 'espace_staff:access')   -- Entraîneur en chef
ON CONFLICT (role_id, permission) DO NOTHING;
