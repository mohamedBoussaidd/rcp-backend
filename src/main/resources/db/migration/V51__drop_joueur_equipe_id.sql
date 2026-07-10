-- ============================================================
-- V51 — Suppression du cache legacy joueur.equipe_id (Phase 4)
--
-- L'appartenance d'équipe d'une personne est désormais portée EXCLUSIVEMENT par effectif_saison
-- (multi-équipe, par saison). Tout le code backend a été basculé sur cette source de vérité
-- (AppartenanceService.equipesDe / equipePrincipale ; requêtes findByEquipeIdIn devenues des
-- jointures sur effectif_saison). Le cache joueur.equipe_id n'est plus ni lu ni écrit.
--
-- Le DROP COLUMN emporte automatiquement l'index idx_joueur_equipe et la FK joueur_equipe_fkey.
-- Effet de bord CORRIGÉ au passage : cette FK était ON DELETE CASCADE → supprimer une équipe
-- supprimait les fiches joueur rattachées. Ce n'est plus le cas (les fiches vivent au niveau club).
-- ============================================================
SET search_path = public;

ALTER TABLE joueur DROP COLUMN IF EXISTS equipe_id;
