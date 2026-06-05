-- ============================================================
-- V15 — Type de résolution d'une gêne traitée
--
-- Distingue, à la clôture d'une gêne, l'archivage simple (« ARCHIVEE »)
-- de la conversion en blessure (« CONVERTIE »). Sert à l'historique des
-- gênes (médical / préparateur). Null tant que la gêne n'est pas traitée.
-- Une gêne rouverte par le médical repasse à null.
-- ============================================================
SET search_path = public;

ALTER TABLE wellness_quotidien ADD COLUMN gene_resolution varchar(20);
