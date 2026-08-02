-- ============================================================
-- V99 — Score détaillé, type de match et seuils de sanction
--
-- TROIS BESOINS, UNE SEULE MIGRATION, parce qu'ils dépendent tous du même constat : le résultat
-- d'un match n'était qu'un texte libre (« 2-1 »), inexploitable par une machine.
--
-- 1. BUTS POUR / CONTRE — le clean sheet de la feuille de match (V97) était une case cochée à la
--    main, donc faux dès qu'on l'oubliait. Il se déduit désormais du score, ce qui exige de savoir
--    lequel des deux nombres est le nôtre. Le champ texte ne le disait pas : rien ne distinguait
--    « 2-1 » gagné de « 2-1 » perdu à l'extérieur. La colonne `score` est CONSERVÉE et reste la
--    source d'affichage partout (liste des matchs, fil de vie) ; elle est simplement reconstruite
--    à l'enregistrement à partir des deux nombres.
--    Le backfill est sûr : les 216 matchs existants sont tous au format `n-n`, et le croisement
--    avec `resultat` le confirme sans une seule exception (V ⇒ premier > second, D ⇒ premier <
--    second, N ⇒ égalité). Le premier nombre est donc bien le nôtre.
--
-- 2. TYPE DE MATCH — le cumul de cartons se compte par compétition : championnat et coupe ont des
--    décomptes séparés, et un amical ne compte jamais. `competition` étant du texte libre, il ne
--    pouvait pas porter cette règle. Il reste comme intitulé d'affichage (« Coupe du district »,
--    « D2 groupe B ») ; c'est `type_match` qui fait foi pour le décompte.
--
-- 3. SEUILS DE SANCTION — deux clés dans `configuration`, parce que le nombre d'avertissements
--    déclenchant une suspension varie selon les districts. Rien n'est codé en dur côté service.
--
-- Aucune table de sanctions n'est créée : `match_suspendu` existe depuis le Match v2 et porte
-- déjà la déclaration. Le calcul ne fait que la SUGGÉRER — c'est la commission qui suspend, pas
-- l'application, et le staff garde la décision.
-- ============================================================
SET search_path = public;

-- ── 1. Score détaillé ──
ALTER TABLE match_prepa
    ADD COLUMN IF NOT EXISTS buts_pour   smallint,
    ADD COLUMN IF NOT EXISTS buts_contre smallint;

-- On ne touche qu'aux scores strictement numériques : un « forfait » ou un « 3-2 tab » resterait
-- interprété de travers, et un clean sheet inventé vaut moins que pas de clean sheet du tout.
UPDATE match_prepa
   SET buts_pour   = split_part(score, '-', 1)::smallint,
       buts_contre = split_part(score, '-', 2)::smallint
 WHERE score ~ '^\s*[0-9]{1,2}\s*-\s*[0-9]{1,2}\s*$'
   AND buts_pour IS NULL;

-- PostgreSQL n'a pas d'ADD CONSTRAINT IF NOT EXISTS : on retire d'abord, sinon une migration
-- interrompue à mi-parcours (ce qui est arrivé ici, sur un redémarrage à chaud) ne se rejoue
-- jamais — elle bute sur la contrainte que sa propre exécution précédente avait posée.
ALTER TABLE match_prepa
    DROP CONSTRAINT IF EXISTS match_prepa_buts_pour_chk,
    DROP CONSTRAINT IF EXISTS match_prepa_buts_contre_chk;
ALTER TABLE match_prepa
    ADD CONSTRAINT match_prepa_buts_pour_chk
        CHECK (buts_pour IS NULL OR (buts_pour >= 0 AND buts_pour <= 99)),
    ADD CONSTRAINT match_prepa_buts_contre_chk
        CHECK (buts_contre IS NULL OR (buts_contre >= 0 AND buts_contre <= 99));

COMMENT ON COLUMN match_prepa.buts_pour IS
    'Buts marqués par NOTRE équipe. Source du score affiché et du clean sheet de la feuille de match.';
COMMENT ON COLUMN match_prepa.buts_contre IS
    'Buts encaissés. À 0, tous les joueurs entrés en jeu obtiennent le clean sheet ; NULL = indéterminé.';

-- `match_compo.clean_sheet` (V97) disparaît : il devient dérivé à la lecture, exactement comme le
-- temps de jeu GPS et pour la même raison — une valeur figée devient fausse dès qu'on corrige le
-- score après coup, et personne ne penserait à rouvrir la feuille de match pour la rafraîchir.
ALTER TABLE match_compo DROP COLUMN IF EXISTS clean_sheet;

-- ── 2. Type de match ──
ALTER TABLE match_prepa
    ADD COLUMN IF NOT EXISTS type_match varchar(20) NOT NULL DEFAULT 'CHAMPIONNAT';

UPDATE match_prepa
   SET type_match = CASE
         WHEN competition ILIKE '%amical%' THEN 'AMICAL'
         WHEN competition ILIKE '%coupe%'  THEN 'COUPE'
         ELSE 'CHAMPIONNAT'
       END;

ALTER TABLE match_prepa DROP CONSTRAINT IF EXISTS match_prepa_type_match_chk;
ALTER TABLE match_prepa
    ADD CONSTRAINT match_prepa_type_match_chk
        CHECK (type_match IN ('AMICAL', 'CHAMPIONNAT', 'COUPE'));

COMMENT ON COLUMN match_prepa.type_match IS
    'AMICAL | CHAMPIONNAT | COUPE. Porte le décompte des cartons ; `competition` reste l''intitulé libre.';

-- Le décompte balaye les matchs d'une équipe sur une saison, filtrés par type.
CREATE INDEX IF NOT EXISTS idx_match_prepa_equipe_type ON match_prepa (equipe_id, type_match, date_match);

-- ── 3. Seuils de sanction ──
-- `configuration` est globale et numérique : elle convient, le seuil de 3 avertissements étant la
-- règle la plus répandue. Le jour où deux clubs de districts différents divergeront, ces clés
-- devront passer par club — d'où le groupe dédié, isolé du reste.
INSERT INTO configuration (cle, valeur, valeur_defaut, groupe, niveau) VALUES
    ('sanctions_seuil_jaunes',      3, 3, 'sanctions', 1),
    ('sanctions_matchs_par_rouge',  1, 1, 'sanctions', 1)
ON CONFLICT (cle) DO NOTHING;
