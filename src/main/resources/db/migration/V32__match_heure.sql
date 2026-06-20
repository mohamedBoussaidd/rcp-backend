-- Heure de coup d'envoi du match (distincte de l'heure de RDV logistique).
ALTER TABLE match_prepa ADD COLUMN heure_match time;
