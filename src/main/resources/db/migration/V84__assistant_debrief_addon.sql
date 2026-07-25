-- ============================================================
-- V84 — Carte IA « Debrief de séance » (add-on)
--
-- Debrief automatique d'une séance réalisée (prévu vs réalisé, objectif de charge atteint,
-- joueurs au-dessus / en-dessous), rédigé par le LLM ou, à défaut, par un gabarit local.
-- Module ADD-ON `assistant_debrief` : dans AUCUN pack, activé club par club par le super-admin.
-- On seed la permission `prepa_ia:debrief` aux rôles qui pilotent la prépa (mêmes que le briefing).
-- ============================================================
SET search_path = public;

INSERT INTO role_permission (role_id, permission)
SELECT r.id, 'prepa_ia:debrief'
FROM (VALUES
   ('a0000000-0000-0000-0000-000000000001'::uuid),   -- PRESIDENT
   ('a0000000-0000-0000-0000-000000000002'::uuid),   -- ENTRAINEUR
   ('a0000000-0000-0000-0000-000000000003'::uuid),   -- PREPARATEUR
   ('a0000000-0000-0000-0000-000000000006'::uuid)    -- ENTRAINEUR_CHEF
) AS r(id)
ON CONFLICT DO NOTHING;
