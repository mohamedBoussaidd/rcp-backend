-- V104 — Lien structurel entre la SÉANCE (socle) et le DOSSIER DE MATCH (add-on).
--
-- Constat : `seance` de type MATCH/MATCH_AMICAL et `match_prepa` décrivaient le même événement
-- sans jamais se connaître. L'appariement n'existait que par (equipe_id, date), implicite, et
-- divergeait dès qu'un humain saisissait d'un seul côté. Deux sources de vérité pour « quand
-- joue-t-on ? » cohabitaient déjà : le badge MD-x lit les séances, l'arbitrage double match lit
-- `match_prepa`.
--
-- Le lien n'est pas inventé ici : `session_gps_id` était DÉJÀ une FK vers `seance`, mais
-- optionnelle, manuelle et nommée comme si elle ne servait qu'au GPS — renseignée 1 fois sur 220.
-- On la PROMEUT en relation structurelle plutôt que d'ajouter une seconde colonne au même rôle.
--
-- Sens de la FK : l'add-on pointe vers le socle, jamais l'inverse. La séance porte le GPS, le RPE,
-- l'appel et la charge — elle existe sans le module Match. Supprimer la séance supprime le dossier
-- (CASCADE) ; supprimer le dossier laisse la séance, qui redevient une séance de match sans volet
-- tactique. C'est ce qui garantit que l'app reste identique module coupé.

-- ── 1. Promotion de la colonne ────────────────────────────────────────────────
ALTER TABLE match_prepa RENAME COLUMN session_gps_id TO seance_id;

ALTER TABLE match_prepa DROP CONSTRAINT IF EXISTS match_prepa_gps_fkey;
ALTER TABLE match_prepa
    ADD CONSTRAINT match_prepa_seance_fkey
    FOREIGN KEY (seance_id) REFERENCES seance(id) ON DELETE CASCADE;

-- ── 2. Backfill ───────────────────────────────────────────────────────────────
-- Trois temps, dans cet ordre : on apparie ce qui va ensemble, PUIS on complète les orphelins.
-- L'inverse créerait des doublons pour toute paire qui s'apparie normalement.
DO $$
DECLARE
    id_match        uuid;
    id_match_amical uuid;
    r               record;
    nouvelle        uuid;
    apparies        int := 0;
    seances_creees  int := 0;
    matchs_crees    int := 0;
BEGIN
    SELECT id INTO id_match        FROM type_seance WHERE code = 'MATCH';
    SELECT id INTO id_match_amical FROM type_seance WHERE code = 'MATCH_AMICAL';
    IF id_match IS NULL THEN
        RAISE EXCEPTION 'Type de séance MATCH introuvable — référentiel incomplet';
    END IF;
    -- Repli si un déploiement n'a pas encore le type amical : il tombe sur MATCH officiel.
    IF id_match_amical IS NULL THEN
        id_match_amical := id_match;
    END IF;

    -- 2a. Appariement (equipe, date). Les deux côtés sont NUMÉROTÉS avant la jointure : sans ça,
    -- une journée à plusieurs matchs pour la même équipe produirait un produit cartésien et
    -- plusieurs dossiers réclameraient la même séance (que l'index unique refuserait ensuite).
    WITH m AS (
        SELECT mp.id, mp.equipe_id, mp.date_match,
               ROW_NUMBER() OVER (PARTITION BY mp.equipe_id, mp.date_match
                                  ORDER BY mp.created_at NULLS LAST, mp.id) AS rn
        FROM match_prepa mp
        WHERE mp.seance_id IS NULL
    ),
    s AS (
        SELECT se.id, se.equipe_id, se.date,
               ROW_NUMBER() OVER (PARTITION BY se.equipe_id, se.date
                                  ORDER BY se.heure_debut NULLS LAST, se.id) AS rn
        FROM seance se
        JOIN type_seance t ON t.id = se.type_seance_id
        WHERE t.code IN ('MATCH', 'MATCH_AMICAL')
          AND NOT EXISTS (SELECT 1 FROM match_prepa mp2 WHERE mp2.seance_id = se.id)
    )
    UPDATE match_prepa mp
       SET seance_id = s.id
      FROM m JOIN s ON s.equipe_id = m.equipe_id AND s.date = m.date_match AND s.rn = m.rn
     WHERE mp.id = m.id;
    GET DIAGNOSTICS apparies = ROW_COUNT;

    -- 2b. Dossiers restés sans séance → on crée la séance. Statut PLANIFIEE même dans le passé :
    -- REALISEE ferait entrer une séance sans la moindre donnée GPS dans les calculs de charge et
    -- de dérives. Une séance planifiée non honorée est neutre, et le staff peut la valider.
    FOR r IN
        SELECT mp.id, mp.equipe_id, mp.date_match, mp.adversaire, mp.type_match,
               mp.domicile, mp.competition
        FROM match_prepa mp
        WHERE mp.seance_id IS NULL AND mp.equipe_id IS NOT NULL
    LOOP
        INSERT INTO seance (type_seance_id, date, equipe_id, statut, adversaire,
                            competition, domicile_exterieur)
        VALUES (CASE WHEN r.type_match = 'AMICAL' THEN id_match_amical ELSE id_match END,
                r.date_match, r.equipe_id, 'PLANIFIEE', r.adversaire, r.competition,
                CASE WHEN r.domicile IS TRUE THEN 'DOMICILE'
                     WHEN r.domicile IS FALSE THEN 'EXTERIEUR' END)
        RETURNING id INTO nouvelle;

        UPDATE match_prepa SET seance_id = nouvelle WHERE id = r.id;
        seances_creees := seances_creees + 1;
    END LOOP;

    -- 2c. Séances de match sans dossier → on crée le dossier, vide mais rattaché. L'adversaire
    -- est repris quand la séance en porte un ; le reste (compo, convocation, feuille) se remplit
    -- dans le module.
    FOR r IN
        SELECT se.id, se.equipe_id, se.date, se.adversaire, se.competition,
               se.domicile_exterieur, t.code AS type_code
        FROM seance se
        JOIN type_seance t ON t.id = se.type_seance_id
        WHERE t.code IN ('MATCH', 'MATCH_AMICAL')
          AND se.equipe_id IS NOT NULL
          AND NOT EXISTS (SELECT 1 FROM match_prepa mp WHERE mp.seance_id = se.id)
    LOOP
        INSERT INTO match_prepa (equipe_id, date_match, adversaire, competition,
                                 type_match, domicile, seance_id)
        VALUES (r.equipe_id, r.date, COALESCE(NULLIF(TRIM(r.adversaire), ''), 'À renseigner'),
                r.competition,
                CASE WHEN r.type_code = 'MATCH_AMICAL' THEN 'AMICAL' ELSE 'CHAMPIONNAT' END,
                -- `domicile` est NOT NULL : une séance qui ne dit rien retombe sur le défaut de
                -- la colonne (domicile), corrigeable en un clic dans le module.
                CASE WHEN r.domicile_exterieur = 'EXTERIEUR' THEN FALSE ELSE TRUE END,
                r.id);
        matchs_crees := matchs_crees + 1;
    END LOOP;

    RAISE NOTICE 'V104 — % appariés, % séances créées, % dossiers de match créés',
                 apparies, seances_creees, matchs_crees;
END $$;

-- ── 3. Un dossier de match au plus par séance ─────────────────────────────────
-- Posé APRÈS le backfill : sur des données historiques désynchronisées, l'index refuserait la
-- migration au lieu de la réparer. Partiel car `seance_id` reste nullable (un dossier peut être
-- détaché à la main sans être supprimé).
CREATE UNIQUE INDEX IF NOT EXISTS match_prepa_seance_unique
    ON match_prepa (seance_id) WHERE seance_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_match_prepa_seance ON match_prepa (seance_id);

COMMENT ON COLUMN match_prepa.seance_id IS
    'Séance du calendrier décrivant le même événement (socle). Portait le GPS du match sous le nom session_gps_id avant V104.';
