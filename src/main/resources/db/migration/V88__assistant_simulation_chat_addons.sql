-- ============================================================
-- V88 — Cartes IA « Simulation » et « Chat » (add-ons)
--
-- Deux modules ADD-ON, dans AUCUN pack, activés club par club par le super-admin :
--   · `assistant_simulation` → permission `prepa_ia:simulation`
--     Simulation d'une séance à venir : distance attendue par joueur (baseline du même type de
--     séance), recalcul de l'ACWR et joueurs qui basculeraient au-dessus du plafond.
--   · `assistant_chat`       → permission `assistant_ia:chat`
--     Assistant conversationnel ancré, LLM obligatoire (aucun repli gabarit).
--
-- Mêmes rôles porteurs que les cartes IA existantes (briefing / debrief / dérives). Le chat est
-- volontairement ouvert aux mêmes rôles pour l'instant : son contexte est filtré à l'exécution par
-- les permissions de chacun (voir ContexteChat), pas par la permission d'accès elle-même.
--
-- Aucune table nouvelle : la surcharge du nom de l'assistant par club réutilise `club_parametre`
-- (clé `nom_assistant`), et l'historique du chat vit côté navigateur.
-- ============================================================
SET search_path = public;

INSERT INTO role_permission (role_id, permission)
SELECT r.id, p.permission
FROM (VALUES
   ('a0000000-0000-0000-0000-000000000001'::uuid),   -- PRESIDENT
   ('a0000000-0000-0000-0000-000000000002'::uuid),   -- ENTRAINEUR
   ('a0000000-0000-0000-0000-000000000003'::uuid),   -- PREPARATEUR
   ('a0000000-0000-0000-0000-000000000006'::uuid)    -- ENTRAINEUR_CHEF
) AS r(id)
CROSS JOIN (VALUES
   ('prepa_ia:simulation'),
   ('assistant_ia:chat')
) AS p(permission)
ON CONFLICT DO NOTHING;
