-- Socle IA multi-clé / multi-provider par club (piloté par le super-admin).
-- club_ia_config : provider + clé API chiffrée + modèle par club (clé jamais en clair).
-- ia_usage : journal de consommation (traçabilité par club + décompte des quotas quotidiens
-- de la clé globale, quand un club n'a pas sa propre clé).

CREATE TABLE club_ia_config (
  club_id          UUID PRIMARY KEY REFERENCES club(id) ON DELETE CASCADE,
  provider         VARCHAR(20)  NOT NULL DEFAULT 'ANTHROPIC',
  cle_api_chiffree TEXT,
  modele           VARCHAR(80)  NOT NULL,
  actif            BOOLEAN      NOT NULL DEFAULT TRUE,
  updated_at       TIMESTAMP
);

CREATE TABLE ia_usage (
  id          UUID PRIMARY KEY,
  club_id     UUID,
  feature     VARCHAR(40) NOT NULL,
  provider    VARCHAR(20),
  modele      VARCHAR(80),
  cle_globale BOOLEAN     NOT NULL DEFAULT FALSE,
  jour        DATE        NOT NULL,
  cree_at     TIMESTAMP   NOT NULL
);

CREATE INDEX idx_ia_usage_club_feature_jour ON ia_usage (club_id, feature, jour);
