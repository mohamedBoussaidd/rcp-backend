-- ============================================================
-- V46 — Échelle wellness (Hooper) et intensité de gêne : 1..5 → 1..10
--
-- Objectif : plus de précision dans la saisie joueur. Convention inchangée :
-- 1 = bon → 10 = mauvais pour TOUS les items. Conversion des données
-- existantes par ×2 (1→2 … 5→10) : l'ordre est conservé et les seuils
-- d'alerte se transposent exactement (ancien ≥4 ≡ nouveau ≥8).
-- Totaux Hooper : 5..25 → 5..50 (l'historique migré occupe 10..50).
-- ============================================================
SET search_path = public;

-- ── Items Hooper ──
ALTER TABLE wellness_quotidien DROP CONSTRAINT wellness_sommeil_chk;
ALTER TABLE wellness_quotidien DROP CONSTRAINT wellness_fatigue_chk;
ALTER TABLE wellness_quotidien DROP CONSTRAINT wellness_douleur_chk;
ALTER TABLE wellness_quotidien DROP CONSTRAINT wellness_stress_chk;
ALTER TABLE wellness_quotidien DROP CONSTRAINT wellness_humeur_chk;
ALTER TABLE wellness_quotidien DROP CONSTRAINT wellness_gene_intensite_chk;

UPDATE wellness_quotidien SET
    sommeil        = sommeil * 2,
    fatigue        = fatigue * 2,
    douleur        = douleur * 2,
    stress         = stress  * 2,
    humeur         = humeur  * 2,
    gene_intensite = gene_intensite * 2;

ALTER TABLE wellness_quotidien ADD CONSTRAINT wellness_sommeil_chk CHECK (sommeil BETWEEN 1 AND 10);
ALTER TABLE wellness_quotidien ADD CONSTRAINT wellness_fatigue_chk CHECK (fatigue BETWEEN 1 AND 10);
ALTER TABLE wellness_quotidien ADD CONSTRAINT wellness_douleur_chk CHECK (douleur BETWEEN 1 AND 10);
ALTER TABLE wellness_quotidien ADD CONSTRAINT wellness_stress_chk  CHECK (stress  BETWEEN 1 AND 10);
ALTER TABLE wellness_quotidien ADD CONSTRAINT wellness_humeur_chk  CHECK (humeur  BETWEEN 1 AND 10);
ALTER TABLE wellness_quotidien ADD CONSTRAINT wellness_gene_intensite_chk
    CHECK (gene_intensite IS NULL OR gene_intensite BETWEEN 1 AND 10);

-- ── Seuils d'alerte par équipe (alerte si valeur >= seuil) ──
UPDATE notif_config_equipe SET
    seuil_wellness_fatigue = LEAST(seuil_wellness_fatigue * 2, 10),
    seuil_wellness_douleur = LEAST(seuil_wellness_douleur * 2, 10),
    seuil_wellness_stress  = LEAST(seuil_wellness_stress  * 2, 10),
    seuil_wellness_sommeil = LEAST(seuil_wellness_sommeil * 2, 10),
    seuil_wellness_humeur  = LEAST(seuil_wellness_humeur  * 2, 10);

ALTER TABLE notif_config_equipe ALTER COLUMN seuil_wellness_fatigue SET DEFAULT 8;
ALTER TABLE notif_config_equipe ALTER COLUMN seuil_wellness_douleur SET DEFAULT 8;
ALTER TABLE notif_config_equipe ALTER COLUMN seuil_wellness_stress  SET DEFAULT 8;
ALTER TABLE notif_config_equipe ALTER COLUMN seuil_wellness_sommeil SET DEFAULT 8;
ALTER TABLE notif_config_equipe ALTER COLUMN seuil_wellness_humeur  SET DEFAULT 8;
