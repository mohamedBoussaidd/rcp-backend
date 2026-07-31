-- ============================================================
-- V91 — Gêne / douleur déclarée dans le questionnaire post-séance
--
-- Jusqu'ici la gêne ne vivait que sur `wellness_quotidien` : UNE par jour et par
-- joueur, sans lien avec une séance. Un joueur qui se fait mal à l'entraînement
-- ne pouvait donc le signaler qu'en écrasant (ou en ratant) sa saisie du matin.
--
-- La gêne post-séance est portée par `rpe_seance` — même vocabulaire que le
-- wellness (zone / intensité 1..10 / moment) pour que les deux sources se lisent
-- côte à côte, PLUS le cycle de traitement staff (traitée / résolution), sans
-- lequel une gêne déclarée après une séance resterait éternellement en alerte.
--
-- `plaisir` et `commentaire` existent déjà (V69 / V10) : ils étaient écrits par
-- l'import CSV mais qu'aucun formulaire ne les saisissait. Rien à ajouter, le
-- lot A se contente de les ouvrir à la saisie.
--
-- Aucune donnée existante n'est touchée : toutes les colonnes sont nullables et
-- gene_zone NULL = aucune gêne sur cette séance (même convention que V13).
-- ============================================================
SET search_path = public;

ALTER TABLE rpe_seance ADD COLUMN gene_zone        varchar(40);
ALTER TABLE rpe_seance ADD COLUMN gene_intensite   smallint;
ALTER TABLE rpe_seance ADD COLUMN gene_moment      varchar(20);
ALTER TABLE rpe_seance ADD COLUMN gene_traitee     boolean NOT NULL DEFAULT false;
ALTER TABLE rpe_seance ADD COLUMN gene_traitee_par uuid;
ALTER TABLE rpe_seance ADD COLUMN gene_traitee_le  timestamp without time zone;
ALTER TABLE rpe_seance ADD COLUMN gene_resolution  varchar(20);

-- Échelle alignée sur celle du wellness depuis V46 (1..10, pas 1..5).
ALTER TABLE rpe_seance ADD CONSTRAINT rpe_gene_intensite_chk
    CHECK (gene_intensite IS NULL OR gene_intensite BETWEEN 1 AND 10);
ALTER TABLE rpe_seance ADD CONSTRAINT rpe_gene_moment_chk
    CHECK (gene_moment IS NULL OR gene_moment IN ('EFFORT', 'APRES', 'REPOS'));
ALTER TABLE rpe_seance ADD CONSTRAINT rpe_gene_resolution_chk
    CHECK (gene_resolution IS NULL OR gene_resolution IN ('ARCHIVEE', 'CONVERTIE'));
ALTER TABLE rpe_seance ADD CONSTRAINT rpe_gene_traitee_par_fkey
    FOREIGN KEY (gene_traitee_par) REFERENCES utilisateur(id) ON DELETE SET NULL;

-- Le plaisir suit la même échelle 1..10 que le RPE ; l'import CSV le bornait déjà
-- côté Java, mais rien ne le garantissait en base.
ALTER TABLE rpe_seance ADD CONSTRAINT rpe_plaisir_chk
    CHECK (plaisir IS NULL OR plaisir BETWEEN 1 AND 10);

-- Les alertes staff balaient les gênes actives récentes : index partiel pour ne
-- pas scanner tout l'historique des RPE (une gêne est rare, la table est dense).
CREATE INDEX idx_rpe_gene_active ON rpe_seance (date DESC)
    WHERE gene_zone IS NOT NULL AND gene_traitee = false;
