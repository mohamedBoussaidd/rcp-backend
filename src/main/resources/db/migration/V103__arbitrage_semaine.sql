-- ============================================================
-- V103 — Arbitrage d'une semaine à deux matchs (brique « double match »)
--
-- La cible hebdomadaire du référentiel INCLUT le match (« 2,8 à 3,5 fois la distance du
-- match » dans le document de référence). Une semaine à deux matchs ne change donc pas la
-- cible : elle change ce qui reste pour l'entraînement. Trois réponses possibles, et aucune
-- n'est bonne dans l'absolu — c'est une décision d'entraîneur, pas un calcul :
--   ALLEGER  (défaut) : cible semaine inchangée, l'entraînement encaisse la différence.
--   ASSUMER  : cible relevée d'un match, l'entraînement ne bouge pas.
--   RELISSER : cible réduite cette semaine, la différence part sur les semaines suivantes.
--
-- POURQUOI UNE COUCHE DE REPORT ET PAS UNE RÉÉCRITURE DE L'OBJECTIF DE PÉRIODE.
-- Réécrire `objectif_periode_valeur` serait plus court, mais la trajectoire d'origine
-- deviendrait illisible : on ne saurait plus si une semaine à 34 km est un choix du modèle ou
-- la conséquence d'un arbitrage pris trois semaines plus tôt. En stockant des DELTAS, le
-- prescrit reste intact, l'écran affiche « 32 km + 2 km reportés de la semaine du 10/03 », et
-- retirer l'arbitrage rétablit tout sans régénérer quoi que ce soit. Une régénération du
-- modèle ne l'écrase pas non plus.
--
-- Les deltas portent aussi le cas ASSUMER (delta positif sur la semaine elle-même), si bien
-- que la lecture est UNIFORME : pour une semaine donnée, le Retenu = prescrit + somme des
-- deltas qui la ciblent, quelle que soit la branche choisie. ALLEGER n'écrit aucun delta —
-- son effet est une dérivation d'affichage (semaine − matchs = entraînement).
-- ============================================================
SET search_path = public;

-- ── La décision, une par équipe et par semaine ──────────────────────────────
-- `date_lundi` est le lundi de la semaine ISO concernée : la même ancre que le panneau
-- « Objectif de la semaine » et que `objectif_periode_valeur.date_lundi`, sinon les deux
-- lectures ne tomberaient pas sur les mêmes bornes.
CREATE TABLE IF NOT EXISTS arbitrage_semaine (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id     uuid NOT NULL REFERENCES club (id)   ON DELETE CASCADE,
    equipe_id   uuid NOT NULL REFERENCES equipe (id) ON DELETE CASCADE,
    date_lundi  date NOT NULL,
    choix       varchar(20) NOT NULL,
    -- Nombre de matchs constaté au moment de l'arbitrage : si le calendrier change ensuite
    -- (match reporté), l'écran peut signaler que la décision ne correspond plus.
    nb_matchs   smallint NOT NULL DEFAULT 2,
    note        text,
    cree_par    uuid,
    created_at  timestamp NOT NULL DEFAULT now(),
    updated_at  timestamp NOT NULL DEFAULT now(),
    CONSTRAINT arbitrage_semaine_choix_chk
        CHECK (choix IN ('ALLEGER', 'ASSUMER', 'RELISSER')),
    CONSTRAINT arbitrage_semaine_unique UNIQUE (equipe_id, date_lundi)
);

CREATE INDEX IF NOT EXISTS idx_arbitrage_semaine_club   ON arbitrage_semaine (club_id);
CREATE INDEX IF NOT EXISTS idx_arbitrage_semaine_equipe ON arbitrage_semaine (equipe_id, date_lundi);

-- ── Les deltas produits par cette décision ──────────────────────────────────
-- Signés, dans l'unité de la métrique (mètres, ou un nombre pour les sprints). La semaine
-- source porte le delta négatif, les semaines qui reçoivent le report portent les positifs :
-- la somme d'un arbitrage RELISSER est nulle, ce qui rend l'invariant vérifiable d'un coup
-- d'œil. Les métriques INTOUCHABLE de la phase ne sont jamais reportées (on sacrifie du
-- volume, jamais l'exposition haute vitesse — risque ischio).
CREATE TABLE IF NOT EXISTS arbitrage_semaine_report (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    arbitrage_id      uuid NOT NULL REFERENCES arbitrage_semaine (id) ON DELETE CASCADE,
    date_lundi_cible  date NOT NULL,
    metrique          varchar(40) NOT NULL,
    delta             integer NOT NULL,
    CONSTRAINT arbitrage_report_unique UNIQUE (arbitrage_id, date_lundi_cible, metrique)
);

CREATE INDEX IF NOT EXISTS idx_arbitrage_report_semaine
    ON arbitrage_semaine_report (date_lundi_cible);
