-- Assignation des tags (badges de portée PLATEFORME) à des entités. Table polymorphe : un tag peut
-- être posé sur un exercice, une séance ou un joueur (extensible sans nouvelle table). L'assignation
-- est faite par le super-admin ; l'affichage est visible par tous les clubs qui voient l'entité.
CREATE TABLE entite_badge (
  id          UUID        PRIMARY KEY,
  type_entite VARCHAR(20) NOT NULL,   -- EXERCICE / SEANCE / JOUEUR
  entite_id   UUID        NOT NULL,
  badge_id    UUID        NOT NULL REFERENCES badge_definition(id) ON DELETE CASCADE,
  cree_at     TIMESTAMP   NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_entite_badge UNIQUE (type_entite, entite_id, badge_id)
);

CREATE INDEX idx_entite_badge_lookup ON entite_badge (type_entite, entite_id);
