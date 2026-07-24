-- ============================================================
-- V78 — Traçabilité de l'origine IA d'une séance (badge « Proposée par IA »)
--
-- Le générateur de séance IA ne créait aucune trace : le brouillon pré-remplit
-- l'éditeur et la séance était sauvegardée comme n'importe quelle séance. Cette
-- colonne marque les séances issues du générateur pour afficher un badge IA.
--   · NULL / 'MANUEL' = saisie manuelle (défaut, aucune rétro-action) ;
--   · 'IA_GENERATION' = brouillon proposé par l'IA puis validé par le coach.
-- Marqueur d'AFFICHAGE uniquement — jamais un critère de sécurité.
-- (Les exercices issus d'un import photo restent tracés par `photo_import_id`,
--  aucune colonne supplémentaire nécessaire de ce côté.)
-- ============================================================
SET search_path = public;

ALTER TABLE seance ADD COLUMN origine varchar(20);
