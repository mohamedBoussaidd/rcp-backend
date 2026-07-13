-- ============================================================
-- V52 — Administratif : lecture des fiches joueurs (débloque Annuaire & fiches)
--
-- Bug de fond : l'Administratif (rôle système 005) a reçu `joueurs:write` en V48 pour créer/
-- éditer les fiches (dont la date de naissance qui pilote la catégorie d'âge), mais PAS
-- `joueurs:read`. Or `GET /api/joueurs/**` (dont `/api/joueurs/annuaire`) exige `joueurs:read`
-- (cf. SecurityConfig). Résultat : l'écran « Annuaire » — pourtant explicitement ouvert à
-- l'Administratif — renvoyait 403, et la liste des fiches de « Mon club » échouait en silence.
--
-- On accorde `joueurs:read` au rôle système 005. Rôle système (club_id NULL) partagé par tous
-- les clubs → s'applique aux clubs existants ET futurs sans retoucher le seeder.
-- ============================================================
SET search_path = public;

INSERT INTO role_permission (role_id, permission) VALUES
 ('a0000000-0000-0000-0000-000000000005','joueurs:read')
ON CONFLICT (role_id, permission) DO NOTHING;
