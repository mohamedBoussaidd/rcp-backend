-- ============================================================
-- V6 — Corrige V5 : retour au vocabulaire météo d'origine du frontend
-- Le formulaire envoie : beau, nuageux, pluie, vent_fort, neige,
-- arret_intemperie. V5 avait (à tort) basculé vers soleil/couvert/vent.
-- On revient au vocabulaire d'origine et on ajoute juste arret_intemperie.
-- ============================================================
SET search_path = public;

ALTER TABLE seance DROP CONSTRAINT IF EXISTS seance_conditions_meteo_check;

-- Remettre les données dans le vocabulaire d'origine (annule V5)
UPDATE seance SET conditions_meteo = 'beau'      WHERE conditions_meteo = 'soleil';
UPDATE seance SET conditions_meteo = 'vent_fort' WHERE conditions_meteo = 'vent';
UPDATE seance SET conditions_meteo = 'orage'     WHERE conditions_meteo = 'couvert';

-- Contrainte = vocabulaire d'origine + arret_intemperie
ALTER TABLE seance ADD CONSTRAINT seance_conditions_meteo_check
    CHECK (conditions_meteo IS NULL OR conditions_meteo IN
        ('beau', 'nuageux', 'pluie', 'vent_fort', 'orage', 'neige', 'arret_intemperie'));
