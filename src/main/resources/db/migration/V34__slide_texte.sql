-- ============================================================
-- V34 — Slide de type TEXTE (diaporama)
-- Ajoute un 4e type de slide : du texte mis en forme (couleur, fond, taille,
-- alignement). Stockage : texte = contenu ; style_json = mise en forme (JSON
-- libre côté front : couleurTexte, couleurFond, taille, alignH, alignV).
-- Les colonnes restent nullables (un slide ne renseigne que les champs de son type).
-- ============================================================
SET search_path = public;

ALTER TABLE slide ADD COLUMN texte      text;
ALTER TABLE slide ADD COLUMN style_json text;
