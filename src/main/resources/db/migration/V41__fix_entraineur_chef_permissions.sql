-- ============================================================
-- V41 — Correctif « Entraîneur en chef » : permissions manquantes
--
-- Le rôle système « Entraîneur en chef » (créé en V39) a recopié la liste de
-- permissions d'ORIGINE de l'Entraîneur (V30), en oubliant celles ajoutées
-- entre-temps à l'Entraîneur : saison (V36) et diaporama (V33). Résultat : le
-- chef avait MOINS de droits qu'un entraîneur normal (ni saison, ni diaporama).
-- On rétablit la parité (chef = entraîneur + admin club).
--
-- Rôle système (club_id NULL) => correction commune à tous les clubs.
-- ============================================================
SET search_path = public;

INSERT INTO role_permission (role_id, permission)
SELECT 'a0000000-0000-0000-0000-000000000006'::uuid, p.perm
FROM (VALUES
   ('saison:read'),('saison:manage'),('diaporama:read'),('diaporama:write')
) AS p(perm)
ON CONFLICT DO NOTHING;
