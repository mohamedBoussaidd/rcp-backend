-- Journal d'exécution des tâches de maintenance (planifiées ou déclenchées manuellement depuis
-- la console super-admin). Sert à afficher le dernier statut/horodatage de chaque tâche.
CREATE TABLE tache_execution (
  id            UUID PRIMARY KEY,
  code          VARCHAR(60)  NOT NULL,
  started_at    TIMESTAMP    NOT NULL,
  finished_at   TIMESTAMP,
  statut        VARCHAR(20)  NOT NULL,
  message       VARCHAR(500),
  declenche_par UUID
);

CREATE INDEX idx_tache_execution_code_date ON tache_execution (code, started_at DESC);
