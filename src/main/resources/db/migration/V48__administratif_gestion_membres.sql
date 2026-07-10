-- ============================================================
-- V48 — Administratif : gestionnaire administratif du club (+ fix affectations Président)
--
-- L'ADMINISTRATIF devient un vrai gestionnaire administratif : en plus de docadmin:* (V47),
-- il gère les COMPTES (staff sauf Président + joueurs), les FICHES joueurs (dont la date de
-- naissance qui pilote la catégorie d'âge) et les ÉQUIPES du club. Sa portée reste le club
-- entier ; il ne gagne AUCUN accès aux données sportives/médicales (aucune permission de ces
-- modules) — seulement l'administration.
--
--   role 005 (ADMINISTRATIF système) += membres:manage + joueurs:write + club:manage
--
-- Ces 3 permissions relèvent de modules SOCLE (Effectif, Administration) → toujours actives.
--
-- FIX transverse (bug de fond) : un Président créé APRÈS la V30 (via création de club) n'avait
-- AUCUNE affectation de rôle → PermissionResolver lui renvoyait zéro permission (il ne pouvait
-- même pas ouvrir « Mon club »). La V30 avait seedé les présidents existants à l'époque ; on
-- rejoue le même backfill ici pour les présidents nés depuis (le code crée désormais l'affectation
-- à la création du club — cf. ClubService).
-- ============================================================
SET search_path = public;

-- ── Permissions de l'Administratif (rôle système 005) ───────────────────────
INSERT INTO role_permission (role_id, permission) VALUES
 ('a0000000-0000-0000-0000-000000000005','membres:manage'),
 ('a0000000-0000-0000-0000-000000000005','joueurs:write'),
 ('a0000000-0000-0000-0000-000000000005','club:manage')
ON CONFLICT (role_id, permission) DO NOTHING;

-- ── Backfill : affectation club-wide pour tout ADMINISTRATIF qui en manque ───
-- (un Administratif pilote le club entier → equipe_id NULL). Idempotent.
INSERT INTO affectation_role (user_id, club_id, equipe_id, role_id)
SELECT u.id, u.club_id, NULL, 'a0000000-0000-0000-0000-000000000005'
FROM utilisateur u
WHERE u.role = 'ADMINISTRATIF'
  AND u.club_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM affectation_role ar
      WHERE ar.user_id = u.id
        AND ar.role_id = 'a0000000-0000-0000-0000-000000000005'
        AND ar.equipe_id IS NULL
  );

-- ── FIX : affectation club-wide pour tout PRÉSIDENT qui en manque ────────────
INSERT INTO affectation_role (user_id, club_id, equipe_id, role_id)
SELECT u.id, u.club_id, NULL, 'a0000000-0000-0000-0000-000000000001'
FROM utilisateur u
WHERE u.role = 'PRESIDENT'
  AND u.club_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM affectation_role ar
      WHERE ar.user_id = u.id
        AND ar.role_id = 'a0000000-0000-0000-0000-000000000001'
        AND ar.equipe_id IS NULL
  );
