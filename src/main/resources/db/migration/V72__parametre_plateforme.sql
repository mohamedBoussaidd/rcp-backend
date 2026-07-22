-- Réglages plateforme (SUPER_ADMIN), distincts des paramètres métier `configuration` des clubs.
-- Accueille pour l'instant la rétention des notifications (purge automatique), et servira de
-- point d'accueil aux futurs réglages d'exploitation.
CREATE TABLE parametre_plateforme (
  cle        VARCHAR(100) PRIMARY KEY,
  valeur     NUMERIC(12, 4) NOT NULL,
  libelle    VARCHAR(160)   NOT NULL,
  updated_at TIMESTAMP      NOT NULL DEFAULT NOW()
);

INSERT INTO parametre_plateforme (cle, valeur, libelle) VALUES
  ('retention_notif_lues_jours',     30, 'Rétention des notifications lues (jours) — au-delà, purge automatique'),
  ('retention_notif_non_lues_jours', 90, 'Rétention des notifications non lues (jours) — au-delà, purge automatique')
ON CONFLICT (cle) DO NOTHING;
