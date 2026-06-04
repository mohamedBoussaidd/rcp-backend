-- ============================================================
-- V5 — Aligner le vocabulaire météo de la séance sur le formulaire
-- Le front envoie (en minuscules) : soleil, nuageux, couvert, pluie,
-- vent, neige, arret_intemperie. L'ancienne contrainte (beau, vent_fort,
-- orage…) bloquait les insertions.
-- ============================================================
SET search_path = public;

-- 1) Retirer l'ancienne contrainte AVANT de migrer les valeurs
--    (sinon l'UPDATE vers 'soleil' violerait l'ancienne contrainte).
ALTER TABLE seance DROP CONSTRAINT IF EXISTS seance_conditions_meteo_check;

-- 2) Migrer les anciennes valeurs vers le nouveau vocabulaire
UPDATE seance SET conditions_meteo = 'soleil'  WHERE conditions_meteo = 'beau';
UPDATE seance SET conditions_meteo = 'vent'    WHERE conditions_meteo = 'vent_fort';
UPDATE seance SET conditions_meteo = 'couvert' WHERE conditions_meteo = 'orage';

-- 3) Recréer la contrainte alignée sur les valeurs du formulaire
ALTER TABLE seance ADD CONSTRAINT seance_conditions_meteo_check
    CHECK (conditions_meteo IS NULL OR conditions_meteo IN
        ('soleil', 'nuageux', 'couvert', 'pluie', 'vent', 'neige', 'arret_intemperie'));
