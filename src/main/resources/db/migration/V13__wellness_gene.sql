-- ============================================================
-- V13 — Signalement de gêne/douleur intégré au ressenti quotidien
--
-- Le joueur peut, dans sa saisie wellness du jour, signaler une gêne localisée
-- (zone + intensité 1..5 + moment). gene_zone NULL = pas de gêne ce jour-là.
-- Sert de détection précoce : remonte en alerte côté staff médical.
-- ============================================================
SET search_path = public;

ALTER TABLE wellness_quotidien ADD COLUMN gene_zone      varchar(40);
ALTER TABLE wellness_quotidien ADD COLUMN gene_intensite smallint;
ALTER TABLE wellness_quotidien ADD COLUMN gene_moment    varchar(20);

ALTER TABLE wellness_quotidien ADD CONSTRAINT wellness_gene_intensite_chk
    CHECK (gene_intensite IS NULL OR gene_intensite BETWEEN 1 AND 5);
ALTER TABLE wellness_quotidien ADD CONSTRAINT wellness_gene_moment_chk
    CHECK (gene_moment IS NULL OR gene_moment IN ('EFFORT', 'APRES', 'REPOS'));
