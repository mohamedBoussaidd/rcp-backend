-- ============================================================
-- V50 — Fiches staff (backfill) : conformité documentaire du staff (Phase 3)
--
-- La conformité documentaire du staff (matriceStaff / endpoint /api/documents-admin/conformite-staff)
-- s'appuie sur des fiches `joueur` (personne) rattachées aux comptes de rôle STAFF via
-- utilisateur.joueur_id. Jusqu'ici seuls les comptes JOUEUR avaient une fiche → la matrice staff
-- restait vide. Ce backfill dote chaque compte STAFF existant (président + encadrants) d'une fiche
-- au niveau CLUB :
--   • equipe_id NULL + champs sportifs NULL (fiche non assignée) ;
--   • liée via utilisateur.joueur_id ;
--   • RESTE HORS des listes joueurs (Phase 2 : une fiche liée à un compte staff et sans effectif
--     n'est pas un « joueur »).
--
-- Idempotent : ne traite que les comptes staff sans fiche (joueur_id IS NULL) rattachés à un club.
-- Les nouveaux comptes staff reçoivent leur fiche à la création (ClubService pour le président,
-- GestionClubService.creerMembre pour les encadrants).
-- ============================================================
SET search_path = public;

DO $$
DECLARE
    r   RECORD;
    fid uuid;
BEGIN
    FOR r IN
        SELECT u.id, u.nom, u.prenom, u.club_id
        FROM utilisateur u
        WHERE u.role IN ('PRESIDENT','ENTRAINEUR','PREPARATEUR','MEDICAL','ADMINISTRATIF')
          AND u.joueur_id IS NULL
          AND u.club_id IS NOT NULL
    LOOP
        fid := uuid_generate_v4();
        INSERT INTO joueur (id, nom, prenom, club_id, statut)
        VALUES (fid,
                COALESCE(NULLIF(r.nom, ''), '—'),
                COALESCE(NULLIF(r.prenom, ''), '—'),
                r.club_id,
                'actif');
        UPDATE utilisateur SET joueur_id = fid WHERE id = r.id;
    END LOOP;
END $$;
