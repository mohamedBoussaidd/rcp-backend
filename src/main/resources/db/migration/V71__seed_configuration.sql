-- Peuplement de la table `configuration` (créée vide par V1__baseline_schema).
-- Historiquement les 47 clés n'étaient insérées que par le script manuel hors Flyway
-- `db/migration_configuration.sql`, lancé en local mais jamais propagé en prod :
-- les groupes ajoutés tardivement (monotonie, récupération, blessures, surpoids,
-- congestion, météo) manquaient donc en prod → PATCH /api/configuration/{cle} = 404.
-- Cette migration idempotente comble les trous SANS écraser les valeurs déjà
-- personnalisées (ON CONFLICT DO NOTHING).

-- ── Groupe 1 : Pondération de charge par type de séance ──────────────────────
INSERT INTO configuration (cle, valeur, valeur_defaut, groupe, niveau) VALUES
  ('poids_match',        1.0000, 1.0000, 'charge_poids', 1),
  ('poids_match_amical', 1.0000, 1.0000, 'charge_poids', 1),
  ('poids_intensif',     0.8500, 0.8500, 'charge_poids', 1),
  ('poids_force',        0.7000, 0.7000, 'charge_poids', 1),
  ('poids_technique',    0.6000, 0.6000, 'charge_poids', 1),
  ('poids_pre_match',    0.5000, 0.5000, 'charge_poids', 1),
  ('poids_reprise',      0.3000, 0.3000, 'charge_poids', 1)
ON CONFLICT (cle) DO NOTHING;

-- ── Groupe 2 : Objectifs GPS par poste (m/min en match officiel) ─────────────
INSERT INTO configuration (cle, valeur, valeur_defaut, groupe, niveau) VALUES
  ('objectif_gardien',            55.0000,  55.0000, 'objectifs_gps', 1),
  ('objectif_defenseur_central',  95.0000,  95.0000, 'objectifs_gps', 1),
  ('objectif_lateral_droit',     105.0000, 105.0000, 'objectifs_gps', 1),
  ('objectif_lateral_gauche',    105.0000, 105.0000, 'objectifs_gps', 1),
  ('objectif_milieu_defensif',   108.0000, 108.0000, 'objectifs_gps', 1),
  ('objectif_milieu_central',    110.0000, 110.0000, 'objectifs_gps', 1),
  ('objectif_milieu_offensif',   108.0000, 108.0000, 'objectifs_gps', 1),
  ('objectif_ailier_droit',      105.0000, 105.0000, 'objectifs_gps', 1),
  ('objectif_ailier_gauche',     105.0000, 105.0000, 'objectifs_gps', 1),
  ('objectif_attaquant',         100.0000, 100.0000, 'objectifs_gps', 1),
  ('objectif_avant_centre',      100.0000, 100.0000, 'objectifs_gps', 1)
ON CONFLICT (cle) DO NOTHING;

-- ── Groupe 3 : Seuils charge hebdomadaire (Signal 1) ────────────────────────
INSERT INTO configuration (cle, valeur, valeur_defaut, groupe, niveau) VALUES
  ('seuil_surcharge_probable', 1.4000, 1.4000, 'seuils_charge', 2),
  ('seuil_surcharge_possible', 1.2000, 1.2000, 'seuils_charge', 2)
ON CONFLICT (cle) DO NOTHING;

-- ── Groupe 4 : Seuils norme GPS (rapport séance + profil joueur) ─────────────
INSERT INTO configuration (cle, valeur, valeur_defaut, groupe, niveau) VALUES
  ('seuil_sous_norme_pct', 20.0000, 20.0000, 'seuils_norme', 2),
  ('seuil_sur_norme_pct',  20.0000, 20.0000, 'seuils_norme', 2)
ON CONFLICT (cle) DO NOTHING;

-- ── Groupe 5 : Seuils dégradation performance (Signal 2) ────────────────────
INSERT INTO configuration (cle, valeur, valeur_defaut, groupe, niveau) VALUES
  ('seuil_mmin_probable',  0.8000, 0.8000, 'seuils_performance', 2),
  ('seuil_mmin_possible',  0.8800, 0.8800, 'seuils_performance', 2),
  ('seuil_vmax_probable',  0.8800, 0.8800, 'seuils_performance', 2),
  ('seuil_vmax_possible',  0.9400, 0.9400, 'seuils_performance', 2),
  ('seuil_hi_probable',    0.7500, 0.7500, 'seuils_performance', 2),
  ('seuil_hi_possible',    0.8500, 0.8500, 'seuils_performance', 2)
ON CONFLICT (cle) DO NOTHING;

-- ── Groupe 6 : Indice de monotonie (Signal 3) ───────────────────────────────
INSERT INTO configuration (cle, valeur, valeur_defaut, groupe, niveau) VALUES
  ('seuil_monotonie_alerte',    2.0000, 2.0000, 'seuils_monotonie', 2),
  ('seuil_monotonie_vigilance', 1.5000, 1.5000, 'seuils_monotonie', 2)
ON CONFLICT (cle) DO NOTHING;

-- ── Groupe 7 : Espacement récupération (Signal 4) ───────────────────────────
INSERT INTO configuration (cle, valeur, valeur_defaut, groupe, niveau) VALUES
  ('delai_match_match_jours',       3.0000, 3.0000, 'recuperation', 2),
  ('delai_intensif_intensif_jours', 2.0000, 2.0000, 'recuperation', 2),
  ('repos_min_14_jours',            4.0000, 4.0000, 'recuperation', 2)
ON CONFLICT (cle) DO NOTHING;

-- ── Groupe 8 : Blessures récentes (bonus fatigue) ───────────────────────────
INSERT INTO configuration (cle, valeur, valeur_defaut, groupe, niveau) VALUES
  ('fenetre_blessure_fatigue_jours', 56.0000, 56.0000, 'blessures', 2),
  ('bonus_blessure_pts',             20.0000, 20.0000, 'blessures', 2)
ON CONFLICT (cle) DO NOTHING;

-- ── Groupe 9 : Correction surpoids ──────────────────────────────────────────
INSERT INTO configuration (cle, valeur, valeur_defaut, groupe, niveau) VALUES
  ('correction_surpoids_pts_par_kg',  5.0000,  5.0000, 'poids_risque', 2),
  ('correction_surpoids_plafond_pts', 20.0000, 20.0000, 'poids_risque', 2),
  ('correction_surpoids_pct_par_kg',   2.0000,  2.0000, 'poids_risque', 2),
  ('correction_surpoids_plafond_pct', 20.0000, 20.0000, 'poids_risque', 2)
ON CONFLICT (cle) DO NOTHING;

-- ── Groupe 10 : Congestion de matchs ────────────────────────────────────────
INSERT INTO configuration (cle, valeur, valeur_defaut, groupe, niveau) VALUES
  ('seuil_congestion_probable', 4.0000, 4.0000, 'congestion', 2),
  ('seuil_congestion_possible', 3.0000, 3.0000, 'congestion', 2)
ON CONFLICT (cle) DO NOTHING;

-- ── Groupe 11 : Correcteurs météo ───────────────────────────────────────────
INSERT INTO configuration (cle, valeur, valeur_defaut, groupe, niveau) VALUES
  ('temp_chaleur_forte_c',       32.0000, 32.0000, 'meteo', 2),
  ('temp_chaleur_moderee_c',     28.0000, 28.0000, 'meteo', 2),
  ('correcteur_chaleur_forte',    0.9000,  0.9000, 'meteo', 2),
  ('correcteur_chaleur_moderee',  0.9500,  0.9500, 'meteo', 2),
  ('correcteur_neige',            0.8800,  0.8800, 'meteo', 2),
  ('correcteur_pluie',            0.9700,  0.9700, 'meteo', 2)
ON CONFLICT (cle) DO NOTHING;
