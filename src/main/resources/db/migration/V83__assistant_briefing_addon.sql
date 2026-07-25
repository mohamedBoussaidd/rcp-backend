-- ============================================================
-- V83 — Carte IA « Briefing du préparateur » (add-on)
--
-- Nouvelle capacité IA du préparateur : une « note du prépa » rédigée à partir des
-- indicateurs déjà calculés (objectif hebdo, charge, vigilances), par le LLM ou, à défaut,
-- par un gabarit local. Module ADD-ON `assistant_briefing` : dans AUCUN pack, activé
-- club par club par le super-admin (comme `import_photo_ia` / `generateur_seance_ia`).
--
-- On seed uniquement la permission `prepa_ia:briefing` aux rôles qui pilotent la prépa
-- (mêmes que `predictions:write`, V82). La visibilité réelle reste conditionnée à
-- l'activation du module pour le club (résolution live pack ∪ overrides) → aucune ligne
-- `pack_module` ici. SUPER_ADMIN a toutes les permissions d'office (hors RBAC).
-- ============================================================
SET search_path = public;

INSERT INTO role_permission (role_id, permission)
SELECT r.id, 'prepa_ia:briefing'
FROM (VALUES
   ('a0000000-0000-0000-0000-000000000001'::uuid),   -- PRESIDENT
   ('a0000000-0000-0000-0000-000000000002'::uuid),   -- ENTRAINEUR
   ('a0000000-0000-0000-0000-000000000003'::uuid),   -- PREPARATEUR
   ('a0000000-0000-0000-0000-000000000006'::uuid)    -- ENTRAINEUR_CHEF
) AS r(id)
ON CONFLICT DO NOTHING;
