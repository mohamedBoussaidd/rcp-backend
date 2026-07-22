-- Cadence configurable des rappels/digest par équipe (jours de la semaine, CSV ISO 1..7).
-- Motivation : le bloc pesée du digest apparaissait tous les jours alors que la pesée est
-- souvent hebdomadaire → bruit. On rend configurables les jours du rappel wellness, les jours
-- d'envoi du digest, et les jours où le bloc poids est inclus (jours de pesée, défaut = lundi).

ALTER TABLE notif_config_equipe
  ADD COLUMN rappel_wellness_jours VARCHAR(20) NOT NULL DEFAULT '1,2,3,4,5,6,7',
  ADD COLUMN digest_jours          VARCHAR(20) NOT NULL DEFAULT '1,2,3,4,5,6,7',
  ADD COLUMN digest_poids_jours    VARCHAR(20) NOT NULL DEFAULT '1';

-- Le rappel pesée joueur n'a jamais été émis (toggle mort) et n'a aucun sens : au club, un
-- membre du staff relève la pesée, le joueur ne saisit pas la sienne. On retire le drapeau.
ALTER TABLE notif_config_equipe DROP COLUMN IF EXISTS rappel_poids_actif;
