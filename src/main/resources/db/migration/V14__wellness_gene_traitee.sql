-- ============================================================
-- V14 — Traitement des signalements de gêne par le staff
--
-- Une gêne signalée par le joueur peut être marquée « traitée » par le staff
-- (médical / préparateur) : elle quitte alors les alertes mais reste en
-- historique. gene_traitee_par / gene_traitee_le = traçabilité.
-- ============================================================
SET search_path = public;

ALTER TABLE wellness_quotidien ADD COLUMN gene_traitee     boolean NOT NULL DEFAULT false;
ALTER TABLE wellness_quotidien ADD COLUMN gene_traitee_par uuid;
ALTER TABLE wellness_quotidien ADD COLUMN gene_traitee_le  timestamp;

ALTER TABLE wellness_quotidien ADD CONSTRAINT wellness_gene_traitee_par_fkey
    FOREIGN KEY (gene_traitee_par) REFERENCES utilisateur(id) ON DELETE SET NULL;
