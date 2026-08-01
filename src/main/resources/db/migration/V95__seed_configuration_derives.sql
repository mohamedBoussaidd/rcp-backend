-- Clés de configuration du bloc « Dérives & surveillance » (carte du dashboard préparateur).
--
-- Jusqu'ici ce bloc était le SEUL calcul du moteur sans aucun garde-fou de quantité de données :
-- il comparait la somme des 14 derniers jours à celle des 14 précédents, avec pour unique
-- protection « référence > 0 ». Un joueur de retour de blessure (1 séance de reprise en
-- référence, ~120 m au-dessus de 19 km/h) ressortait donc à +1900 % — un dénominateur minuscule,
-- pas une dérive. Le seuil de dérive lui-même était codé en dur à 20 % dans le Python.
--
-- Ces 5 clés rendent pilotables : le seuil de dérive, le nombre minimum de séances exigé DANS
-- CHAQUE fenêtre, le minimum de jours de ressenti, et les planchers absolus sous lesquels une
-- référence est jugée trop maigre pour être comparée.
--
-- ⚠ `derive_seuil_pct` reprend EXACTEMENT la valeur codée en dur aujourd'hui (20 %) : la poser ne
-- change rien. Les quatre autres clés sont NOUVELLES et introduisent volontairement un
-- comportement plus prudent — des joueurs jusqu'ici affichés avec des pourcentages absurdes sont
-- désormais écartés et comptés dans `nb_ecartes`.
-- Idempotente (ON CONFLICT DO NOTHING) : une valeur déjà personnalisée n'est jamais écrasée.

INSERT INTO configuration (cle, valeur, valeur_defaut, groupe, niveau) VALUES
  ('derive_seuil_pct',            20.0000,   20.0000,   'derives', 2),
  ('derive_min_seances',           3.0000,    3.0000,   'derives', 2),
  ('derive_min_jours_ressenti',    3.0000,    3.0000,   'derives', 2),
  ('derive_plancher_volume_m',  3000.0000, 3000.0000,   'derives', 2),
  ('derive_plancher_hi_m',       300.0000,  300.0000,   'derives', 2)
ON CONFLICT (cle) DO NOTHING;

COMMENT ON TABLE configuration IS
  'Réglages du moteur d''analyse, lus par _load_config() côté Python. Toute clé lue par le moteur doit y être semée : sans ligne, le moteur retombe en silence sur son défaut codé en dur.';
