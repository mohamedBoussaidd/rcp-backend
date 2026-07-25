-- ============================================================
-- V85 — Carte IA « Dérives & surveillance » (add-on)
--
-- Surveillance des dérives lentes de charge de l'effectif sur ~4 semaines (volume, haute
-- intensité, ressenti), avec synthèse rédigée par le LLM ou, à défaut, par un gabarit local.
-- Module ADD-ON `assistant_derives` : dans AUCUN pack, activé club par club par le super-admin.
-- On seed la permission `prepa_ia:derives` aux rôles qui pilotent la prépa (mêmes que le briefing).
-- ============================================================
SET search_path = public;

INSERT INTO role_permission (role_id, permission)
SELECT r.id, 'prepa_ia:derives'
FROM (VALUES
   ('a0000000-0000-0000-0000-000000000001'::uuid),   -- PRESIDENT
   ('a0000000-0000-0000-0000-000000000002'::uuid),   -- ENTRAINEUR
   ('a0000000-0000-0000-0000-000000000003'::uuid),   -- PREPARATEUR
   ('a0000000-0000-0000-0000-000000000006'::uuid)    -- ENTRAINEUR_CHEF
) AS r(id)
ON CONFLICT DO NOTHING;
