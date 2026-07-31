-- ============================================================
-- V94 — La couleur d'un type de séance devient un réglage PAR CLUB
--
-- PROBLÈME introduit par la V93 : `type_seance` est un catalogue GLOBAL (aucun
-- club_id), et la permission `typeseances:write` est détenue par 4 rôles dans
-- CHAQUE club (Président, Entraîneur, Entraîneur en chef, Préparateur). Exposer
-- l'édition de la couleur revenait donc à laisser n'importe quel entraîneur
-- repeindre le calendrier de tous les clubs de la plateforme.
--
-- CORRECTIF : la couleur suit exactement le modèle déjà en place pour les cibles
-- physiques (V23) — une valeur par défaut au niveau du catalogue, surchargeable
-- par club dans `type_seance_cible` :
--     couleur affichée = type_seance_cible.couleur (club) ?? type_seance.couleur (défaut)
--
-- `type_seance.profil` reste global — il décrit la NATURE du type (une séance de
-- musculation en est une partout) — mais son édition passe désormais en
-- SUPER_ADMIN uniquement, côté contrôleur.
-- ============================================================
SET search_path = public;

ALTER TABLE type_seance_cible ADD COLUMN couleur varchar(9);

COMMENT ON COLUMN type_seance_cible.couleur IS
  'Couleur du type pour CE club. NULL = on retombe sur type_seance.couleur (défaut plateforme).';
