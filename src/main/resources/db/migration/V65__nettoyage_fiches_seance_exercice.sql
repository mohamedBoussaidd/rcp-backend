-- V65 — Nettoyage des fiches Exercice & Séance.
--
-- Audit de départ : le problème n'était pas « trop de champs » mais « la même information
-- saisissable de plusieurs façons selon l'onglet ». Cette migration supprime les doublons et
-- installe à la place une chaîne lisible sur la séance :
--
--     CONTEXTE (le problème constaté, staff)  →  OBJECTIF (ce qu'on vise)  →  DÉROULÉ
--
-- Principe tenu partout ici : AUCUNE donnée saisie n'est perdue. Tout champ supprimé est
-- d'abord reversé quelque part (contexte → séance, sinon description ; responsable → compte
-- staff, sinon description).
--
-- ⚠ Deux suppressions annoncées au plan ne sont volontairement PAS faites, après vérification :
--   · seance.heure_fin : lue par NotificationScheduler (heure du rappel RPE) et écrite par
--     l'import GPS python. Elle est absente du formulaire, pas morte.
--   · seance.titre : alimentée par planifier(), MatchService et les modèles de semaine, et lue
--     par PresenceService. Elle cesse d'être SAISIE (retirée du formulaire) et devient un
--     libellé dérivé — la supprimer casserait quatre services pour un gain nul.

-- ════════════ EXERCICE ════════════

-- ── contexte_pedagogique : il décrit un moment précis (« séance à J+2 »), il n'a rien à faire
--    sur un objet de bibliothèque réutilisé dans 50 séances. Il remonte sur la séance.

-- Exercices utilisés dans UNE seule séance : le contexte y est transposable sans ambiguïté.
-- array_agg plutôt que MIN : Postgres n'agrège pas les uuid, et le HAVING garantit de toute
-- façon qu'il n'y a qu'une seule séance dans le tableau.
CREATE TEMP TABLE ctx_deplace ON COMMIT DROP AS
SELECT se.exercice_id, (array_agg(DISTINCT se.seance_id))[1] AS seance_id
FROM seance_exercice se
GROUP BY se.exercice_id
HAVING COUNT(DISTINCT se.seance_id) = 1;

ALTER TABLE seance ADD COLUMN contexte text;

COMMENT ON COLUMN seance.contexte IS
    'Problème constaté qui motive la séance (ex. « 3 buts encaissés sur CPA samedi »). '
    'STAFF UNIQUEMENT : jamais exposé dans la vue joueur de la fiche.';

UPDATE seance s
SET    contexte = btrim(e.contexte_pedagogique)
FROM   ctx_deplace d
JOIN   exercice e ON e.id = d.exercice_id
WHERE  s.id = d.seance_id
  AND  s.contexte IS NULL
  AND  e.contexte_pedagogique IS NOT NULL
  AND  btrim(e.contexte_pedagogique) <> '';

-- Exercices partagés entre plusieurs séances (ou aucune) : on ne peut pas choisir une séance
-- destinataire, donc on conserve le texte dans la description plutôt que de le jeter.
UPDATE exercice e
SET    description = concat_ws(' · ', nullif(btrim(e.description), ''),
                               'Contexte : ' || btrim(e.contexte_pedagogique))
WHERE  e.contexte_pedagogique IS NOT NULL
  AND  btrim(e.contexte_pedagogique) <> ''
  AND  NOT EXISTS (SELECT 1 FROM ctx_deplace d WHERE d.exercice_id = e.id);

ALTER TABLE exercice DROP COLUMN contexte_pedagogique;

-- ── but_systeme_marque + regles_jeu : deux champs pour une même chose (« comment on marque »
--    fait partie des règles). On les fusionne dans regles_jeu.
UPDATE exercice
SET    regles_jeu = concat_ws(' · ', nullif(btrim(but_systeme_marque), ''),
                                     nullif(btrim(regles_jeu), ''))
WHERE  nullif(btrim(but_systeme_marque), '') IS NOT NULL;

ALTER TABLE exercice DROP COLUMN but_systeme_marque;

COMMENT ON COLUMN exercice.regles_jeu IS
    'Règles du jeu ET système de marque (fusionnés en V65 : « 1 but = 1 pt » est une règle).';

-- ── sequencage : le même champ existait sur l'exercice ET sur le bloc, sans règle de priorité.
--    C'est un paramètre d'exécution du jour → il ne vit plus que sur le bloc (cf. V66).
--    Aucune destination automatique possible (un exercice n'appartient pas à un bloc en propre),
--    donc on conserve la valeur dans la description.
UPDATE exercice
SET    description = concat_ws(' · ', nullif(btrim(description), ''),
                               'Séquençage : ' || btrim(sequencage))
WHERE  nullif(btrim(sequencage), '') IS NOT NULL;

ALTER TABLE exercice DROP COLUMN sequencage;

-- ════════════ SÉANCE ════════════

-- ── Lien facultatif vers ce qui a déclenché la séance. Un match EST une séance dans ce modèle
--    (adversaire / competition / score_match sont portés par `seance`), donc une seule colonne
--    suffit pour pointer aussi bien un match qu'une séance précédente.
--    ON DELETE SET NULL : supprimer le match d'origine ne doit pas supprimer la séance qu'il a
--    motivée — on perd le lien, pas le travail.
ALTER TABLE seance ADD COLUMN contexte_seance_id uuid
    REFERENCES seance(id) ON DELETE SET NULL;

CREATE INDEX idx_seance_contexte_seance ON seance(contexte_seance_id)
    WHERE contexte_seance_id IS NOT NULL;

COMMENT ON COLUMN seance.contexte_seance_id IS
    'Séance ou match à l''origine du contexte (facultatif). Permettra de répondre à '
    '« qu''a-t-on travaillé après chaque défaite ? » sans nouvelle migration.';

-- ── responsable : texte libre (« ex : Rémi. ») qui doublonnait une donnée structurée (les
--    comptes staff, déjà utilisés pour l'affectation aux blocs). On le relie à un vrai compte.
ALTER TABLE seance ADD COLUMN responsable_id uuid
    REFERENCES utilisateur(id) ON DELETE SET NULL;

-- Rapprochement par nom, dans le club de l'équipe de la séance uniquement.
UPDATE seance s
SET    responsable_id = u.id
FROM   equipe eq, utilisateur u
WHERE  s.equipe_id = eq.id
  AND  u.club_id = eq.club_id
  AND  nullif(btrim(s.responsable), '') IS NOT NULL
  AND  lower(btrim(s.responsable)) IN (
           lower(btrim(coalesce(u.prenom, '') || ' ' || coalesce(u.nom, ''))),
           lower(btrim(coalesce(u.nom, ''))),
           lower(btrim(coalesce(u.prenom, ''))));

-- Ce qui n'a pas trouvé de compte (surnoms, « Rémi. », staff sans compte) n'est PAS versé dans
-- la description : celle-ci est du contenu que le coach a écrit, et sur les jeux générés le
-- rapprochement échoue en masse — on polluerait des milliers de séances avec une même mention.
-- Le texte reste donc dans sa propre colonne, en LECTURE SEULE : plus jamais saisie, affichée
-- seulement à défaut de compte, et qui disparaît d'elle-même dès qu'on en désigne un.
ALTER TABLE seance RENAME COLUMN responsable TO responsable_texte;

COMMENT ON COLUMN seance.responsable_texte IS
    'LEGACY (avant V65) : nom du responsable tapé à la main. Lecture seule, affiché uniquement '
    'quand responsable_id est vide. Ne jamais réécrire — la saisie passe par responsable_id.';
