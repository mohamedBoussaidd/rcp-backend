-- V64 — Bibliothèque de schémas FOURNIS (globaux), posée par le super-admin.
--
-- Problème résolu : un club qui vient d'être créé ouvre une bibliothèque de schémas vide et
-- n'a aucun point de départ. On introduit donc des schémas « fournis », visibles par TOUS les
-- clubs, que chacun copie dans sa propre bibliothèque pour les adapter.
--
-- Patron repris tel quel de `profil_import_gps` (V57) : club_id NULL = enregistrement global,
-- listé en plus de ceux du club, jamais modifiable sur place, cloné à l'usage.
--
-- Volontairement PAS de module de pack : l'intérêt est justement que tous les clubs, quel que
-- soit leur abonnement, démarrent avec du contenu plutôt que sur une page vide.

ALTER TABLE schema_tactique ALTER COLUMN club_id DROP NOT NULL;

COMMENT ON COLUMN schema_tactique.club_id IS
    'NULL = schéma FOURNI (global, super-admin), visible par tous les clubs et copiable ; '
    'sinon schéma appartenant au club.';

-- Un schéma fourni n'a pas de créateur rattaché à un club : on s'appuie sur club_id IS NULL
-- pour les retrouver, d'où l'index partiel (la liste des fournis est lue à chaque affichage
-- de la bibliothèque, par tous les clubs).
CREATE INDEX idx_schema_tactique_fournis ON schema_tactique(updated_at DESC) WHERE club_id IS NULL;
