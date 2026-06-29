-- ============================================================
-- V40 — Origine de la saisie de présence (appel staff vs auto-déclaration joueur PWA)
-- La table presence existe depuis V21 (statut varchar depuis V25). On ajoute la
-- colonne `source` pour distinguer une ligne saisie par le staff (appel) d'une ligne
-- déclarée par le joueur depuis la PWA, afin de l'afficher (« déclaré par le joueur »).
-- ============================================================
SET search_path = public;

ALTER TABLE presence ADD COLUMN source varchar(20) NOT NULL DEFAULT 'STAFF';

COMMENT ON COLUMN presence.source IS 'Origine de la saisie : STAFF (appel) ou JOUEUR (auto-déclaration PWA).';
