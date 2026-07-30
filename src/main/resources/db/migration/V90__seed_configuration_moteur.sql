-- Complète la table `configuration` avec les clés que le moteur d'analyse LIT déjà mais qui
-- n'ont jamais été semées : `_load_config()` (python/app/api/routes/predictions.py) les cherche
-- en base et, faute de ligne, retombe silencieusement sur la valeur codée en dur. Résultat :
-- 18 réglages structurants (fenêtre ACWR, zones cibles, seuils de ressenti, seuils sRPE,
-- fraîcheur des données…) étaient FIGÉS et invisibles — ni modifiables dans /admin/parametres,
-- ni repérables autrement qu'en lisant le code Python.
--
-- S'y ajoutent 4 nouvelles clés du Signal 2 : le nombre de séances récentes analysé par
-- sous-signal était figé à 2, ce qui est très court pour juger une vitesse de pointe.
--
-- ⚠ Les valeurs insérées sont EXACTEMENT les défauts codés en dur aujourd'hui : poser cette
-- migration ne change donc AUCUN calcul. Elle rend seulement pilotable ce qui ne l'était pas.
-- Idempotente (ON CONFLICT DO NOTHING) : une valeur déjà personnalisée n'est jamais écrasée.

-- ── Fenêtre et zones cibles de l'ACWR ───────────────────────────────────────
INSERT INTO configuration (cle, valeur, valeur_defaut, groupe, niveau) VALUES
  ('acwr_semaines_chronique', 4.0000, 4.0000, 'acwr', 2),
  ('acwr_cible_min',          0.8000, 0.8000, 'acwr', 2),
  ('acwr_cible_ideal',        1.0500, 1.0500, 'acwr', 2),
  ('acwr_cible_haute',        1.2000, 1.2000, 'acwr', 2),
  ('acwr_cible_max',          1.3000, 1.3000, 'acwr', 2)
ON CONFLICT (cle) DO NOTHING;

-- ── Combinaison des deux sources de charge (GPS externe / sRPE ressentie) ───
INSERT INTO configuration (cle, valeur, valeur_defaut, groupe, niveau) VALUES
  ('poids_charge_gps',     0.6000, 0.6000, 'sources_charge', 2),
  ('poids_charge_rpe',     0.4000, 0.4000, 'sources_charge', 2),
  ('seuil_ecart_sources',  0.3000, 0.3000, 'sources_charge', 2)
ON CONFLICT (cle) DO NOTHING;

-- ── Ressenti quotidien (indice de Hooper) ───────────────────────────────────
INSERT INTO configuration (cle, valeur, valeur_defaut, groupe, niveau) VALUES
  ('seuil_wellness_alerte',    40.0000, 40.0000, 'seuils_wellness', 2),
  ('seuil_wellness_vigilance', 55.0000, 55.0000, 'seuils_wellness', 2)
ON CONFLICT (cle) DO NOTHING;

-- ── Charge ressentie (sRPE) ─────────────────────────────────────────────────
INSERT INTO configuration (cle, valeur, valeur_defaut, groupe, niveau) VALUES
  ('seuil_srpe_probable', 1.5000, 1.5000, 'seuils_srpe', 2),
  ('seuil_srpe_possible', 1.3000, 1.3000, 'seuils_srpe', 2)
ON CONFLICT (cle) DO NOTHING;

-- ── Signal 2 : nombre de séances analysées + seuils de capacité ─────────────
-- Les 4 premières clés sont NOUVELLES : jusqu'ici le moteur comparait les 2 dernières séances
-- à leurs devancières, sans possibilité de rallonger la fenêtre pour lisser le bruit.
INSERT INTO configuration (cle, valeur, valeur_defaut, groupe, niveau) VALUES
  ('nb_seances_recentes_intensite', 2.0000, 2.0000, 'seuils_performance', 2),
  ('nb_seances_recentes_vmax',      2.0000, 2.0000, 'seuils_performance', 2),
  ('nb_seances_recentes_hi',        2.0000, 2.0000, 'seuils_performance', 2),
  ('nb_seances_reference_min',      2.0000, 2.0000, 'seuils_performance', 2),
  ('seuil_vmax_capacite_possible',  0.9300, 0.9300, 'seuils_performance', 2),
  ('seuil_vmax_capacite_probable',  0.9000, 0.9000, 'seuils_performance', 2),
  ('seuil_sprint_corroboration',    0.8000, 0.8000, 'seuils_performance', 2)
ON CONFLICT (cle) DO NOTHING;

-- ── Fraîcheur des données et baseline historique ────────────────────────────
INSERT INTO configuration (cle, valeur, valeur_defaut, groupe, niveau) VALUES
  ('jours_inactif_max',       10.0000, 10.0000, 'fraicheur', 2),
  ('baseline_recence_jours',  90.0000, 90.0000, 'fraicheur', 2),
  ('tendance_seuil_pts',       5.0000,  5.0000, 'fraicheur', 2)
ON CONFLICT (cle) DO NOTHING;
