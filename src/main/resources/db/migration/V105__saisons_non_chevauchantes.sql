-- V105 — Fin des saisons qui se recouvrent.
--
-- `cloturer()` et `ouvrir()` changeaient le STATUT de la saison précédente sans jamais toucher à
-- sa `date_fin`. Résultat observé en base : « Saison 2025-2026 » courait jusqu'au 2026-08-04 alors
-- que « 2026-2027 » démarrait le 2026-07-01 — cinq semaines pendant lesquelles une séance
-- appartenait aux deux saisons.
--
-- C'est bloquant pour le scoping par saison : aucune donnée datée ne porte de `saison_id`, le
-- rattachement se déduit de la date. Tant que deux fenêtres se recouvrent, « les séances de la
-- saison X » n'a pas de réponse unique.
--
-- Règle appliquée : quand deux saisons d'un même club se chevauchent, celle qui commence le plus
-- tôt s'arrête la veille du début de la suivante. On ne déplace jamais une date de DÉBUT (elle a
-- été choisie), jamais une saison EN_COURS (c'est la vie du club aujourd'hui) — uniquement la fin
-- de la plus ancienne, qui n'était de toute façon qu'un reliquat non mis à jour à la clôture.

DO $$
DECLARE
    r        record;
    bornees  int := 0;
BEGIN
    FOR r IN
        SELECT s.id, s.libelle, s.date_fin, suivante.date_debut AS debut_suivante
        FROM saison s
        JOIN LATERAL (
            SELECT s2.date_debut
            FROM saison s2
            WHERE s2.club_id = s.club_id
              AND s2.id <> s.id
              AND s2.date_debut > s.date_debut
            ORDER BY s2.date_debut
            LIMIT 1
        ) suivante ON TRUE
        WHERE s.date_fin >= suivante.date_debut
    LOOP
        UPDATE saison SET date_fin = r.debut_suivante - 1 WHERE id = r.id;
        bornees := bornees + 1;
        RAISE NOTICE 'V105 — saison « % » bornée : % -> %',
                     r.libelle, r.date_fin, r.debut_suivante - 1;
    END LOOP;

    RAISE NOTICE 'V105 — % saison(s) bornée(s)', bornees;
END $$;

-- Les périodes de saison suivent leur saison : une période qui dépasse la nouvelle date de fin
-- serait invisible côté application (la résolution de période borne sur la saison) mais fausserait
-- le bilan de période, qui itère les semaines entre date_debut et date_fin.
UPDATE periode_saison p
   SET date_fin = s.date_fin
  FROM saison s
 WHERE s.id = p.saison_id
   AND p.date_fin > s.date_fin;

DELETE FROM periode_saison p
 USING saison s
 WHERE s.id = p.saison_id
   AND p.date_debut > s.date_fin;
