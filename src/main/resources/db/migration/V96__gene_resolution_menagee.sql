-- ============================================================
-- V96 — Troisième issue d'une gêne : MENAGEE
--
-- V91 a posé rpe_gene_resolution_chk avec les deux seules issues connues à
-- l'époque : ARCHIVEE (rien à faire) et CONVERTIE (devient une blessure). Le
-- lot « présence aménagée » (V95) en a ouvert une troisième — le médical déclare
-- le joueur adapté / au soin sur une période — et la clôture correspondante
-- écrit MENAGEE, que la contrainte refusait : l'aménagement partait bien, mais
-- la gêne restait en alerte avec « la gêne n'a pas pu être clôturée ».
--
-- Le symptôme ne touchait QUE les gênes post-séance : la même colonne sur
-- wellness_quotidien (V15) n'a jamais eu de CHECK, donc une gêne du ressenti du
-- jour se clôturait déjà. On ne pose pas de contrainte sur wellness ici — ce
-- serait un durcissement sans rapport avec le correctif.
--
-- Aucune donnée existante n'est touchée : la contrainte est élargie, jamais
-- resserrée, donc toutes les lignes en base la satisfont déjà.
-- ============================================================
SET search_path = public;

ALTER TABLE rpe_seance DROP CONSTRAINT IF EXISTS rpe_gene_resolution_chk;
ALTER TABLE rpe_seance ADD CONSTRAINT rpe_gene_resolution_chk
    CHECK (gene_resolution IS NULL OR gene_resolution IN ('ARCHIVEE', 'CONVERTIE', 'MENAGEE'));
