-- Personnalisation visuelle par club : couleur d'accent + barre de navigation teintée.
-- couleur_accent NULL = thème par défaut (vert).
ALTER TABLE club ADD COLUMN couleur_accent VARCHAR(7);
ALTER TABLE club ADD COLUMN nav_teintee BOOLEAN NOT NULL DEFAULT FALSE;
