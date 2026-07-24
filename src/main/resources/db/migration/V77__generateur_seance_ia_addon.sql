-- ============================================================
-- V77 — Générateur de séance IA en add-on + IA 100 % hors pack
--
-- Décision produit (2026-07-24) : les DEUX features IA (générateur de séance ET
-- import photo) sont des ADD-ONS purs, dans AUCUN pack, désactivés par défaut,
-- pour maîtriser le coût et l'usage de l'IA. Le super-admin les active club par
-- club (table club_module). La résolution des modules étant live (pack ∪
-- surcharges), retirer un module d'un pack le désactive immédiatement partout
-- où il n'a pas de surcharge explicite. Aucune donnée n'est touchée : un club
-- qui perd l'add-on garde ses séances/exercices visibles, seul le bouton IA
-- disparaît jusqu'à réactivation.
--
--  · nouveau module `generateur_seance_ia` : dans aucun pack (add-on) ;
--  · permission `seance_ia:generate` : distribuée aux rôles qui préparent les
--    séances (Président, Entraîneur, Préparateur, Entraîneur en chef), comme
--    import_photo:use — le module (off par défaut) reste le vrai garde-fou ;
--  · retrait de `import_photo_ia` du pack Complet → lui aussi 100 % add-on.
-- ============================================================
SET search_path = public;

-- Permission d'usage du générateur (mêmes rôles préparateurs de séance que l'import photo).
INSERT INTO role_permission (role_id, permission) VALUES
 ('a0000000-0000-0000-0000-000000000001', 'seance_ia:generate'),
 ('a0000000-0000-0000-0000-000000000002', 'seance_ia:generate'),
 ('a0000000-0000-0000-0000-000000000003', 'seance_ia:generate'),
 ('a0000000-0000-0000-0000-000000000006', 'seance_ia:generate')
ON CONFLICT (role_id, permission) DO NOTHING;

-- Le module `generateur_seance_ia` n'est ajouté à AUCUN pack (add-on à la carte).

-- Retrait de l'import photo IA de tous les packs (il était sur `complet`) → add-on pur.
DELETE FROM pack_module WHERE module_code = 'import_photo_ia';
