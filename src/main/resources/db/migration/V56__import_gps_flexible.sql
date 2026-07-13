-- ============================================================
-- V56 — Import GPS flexible : profils de mapping + alias joueurs
--
-- L'import GPS était 100 % positionnel (colonnes 0-12 figées, xlsx seul) : tout export d'une
-- structure différente échouait, ou pire, écrivait des valeurs dans les mauvais champs.
--
--   1. `profil_import_gps` : mapping colonnes→métriques par CLUB, reconnu automatiquement aux
--      imports suivants via la signature des en-têtes (normalisés : minuscules, sans accents,
--      espaces réduits, joints par '|'). `club_id NULL` = profil GLOBAL fournisseur (même
--      pattern que les rôles globaux) proposé à tous les clubs au premier import.
--      `mappings` = JSON : [{entete, metrique, facteur, seuilReel, semantique CUMUL|BANDE,
--      formatDuree}] — BANDE = distance sur une plage (ex. 24-28 km/h) que le convertisseur
--      re-cumule vers les colonnes cumulatives de donnee_gps (d24 = bande(24-28) + d>28).
--
--   2. `alias_joueur_import` : mémorise les résolutions MERGE (« K. Benali » → fiche) pour ne
--      résoudre chaque graphie qu'UNE fois par club. Alias stocké normalisé.
--
-- Seed : profil global « McLloyd » calibré sur un export réel (doc/import_exemple_donnee_brut.xlsx).
-- Aucune donnée métier touchée. Module `gps` inchangé (packs Performance/Complet, perm gps:import).
-- ============================================================
SET search_path = public;

CREATE TABLE profil_import_gps (
    id                 uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    club_id            uuid REFERENCES club(id) ON DELETE CASCADE,  -- NULL = profil global fournisseur
    nom                varchar(120) NOT NULL,
    signature_entetes  text NOT NULL,
    format_identite    varchar(20) NOT NULL DEFAULT 'PRENOM_NOM',   -- PRENOM | PRENOM_NOM | NOM_PRENOM
    mappings           text NOT NULL,                               -- JSON [{entete, metrique, ...}]
    created_at         timestamp NOT NULL DEFAULT now(),
    updated_at         timestamp NOT NULL DEFAULT now()
);

CREATE INDEX idx_profil_import_gps_club ON profil_import_gps(club_id);
CREATE INDEX idx_profil_import_gps_signature ON profil_import_gps(signature_entetes);

CREATE TABLE alias_joueur_import (
    id          uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    club_id     uuid NOT NULL REFERENCES club(id) ON DELETE CASCADE,
    alias       varchar(160) NOT NULL,                              -- normalisé (minuscules, sans accents)
    joueur_id   uuid NOT NULL REFERENCES joueur(id) ON DELETE CASCADE,
    created_at  timestamp NOT NULL DEFAULT now(),
    UNIQUE (club_id, alias)
);

CREATE INDEX idx_alias_joueur_import_joueur ON alias_joueur_import(joueur_id);

-- ── Seed : profil global « McLloyd » (export réel fourni par le user, 14 colonnes) ──
-- La signature DOIT rester identique à celle produite par ImportNormalisation.normalise() côté Java
-- (NFKD sans marques combinantes, minuscules, espaces réduits — '²' devient '2').
INSERT INTO profil_import_gps (id, club_id, nom, signature_entetes, format_identite, mappings) VALUES (
 'f0000000-0000-0000-0000-000000000001',
 NULL,
 'McLloyd',
 'activity date|capteur|numero de joueur|nom de joueur|temps joue|distance (m)|distance hid (>15 km/h)|distance hid (>19 km/h)|distance par plage de vitesse (24-28 km/h)|distance par plage de vitesse (>28 km/h)|# of sprints (>24 km/h)|vitesse max (km/h)|# of accelerations (>3 m/s2)|# of decelerations (>3 m/s2)',
 'PRENOM_NOM',
 '[
   {"entete":"activity date","metrique":"DATE_SEANCE"},
   {"entete":"nom de joueur","metrique":"IDENTITE"},
   {"entete":"temps joue","metrique":"DUREE","formatDuree":"HMS"},
   {"entete":"distance (m)","metrique":"DISTANCE_TOTALE"},
   {"entete":"distance hid (>15 km/h)","metrique":"DISTANCE_Z15","seuilReel":15,"semantique":"CUMUL"},
   {"entete":"distance hid (>19 km/h)","metrique":"DISTANCE_Z19","seuilReel":19,"semantique":"CUMUL"},
   {"entete":"distance par plage de vitesse (24-28 km/h)","metrique":"DISTANCE_Z24","seuilReel":24,"semantique":"BANDE"},
   {"entete":"distance par plage de vitesse (>28 km/h)","metrique":"DISTANCE_Z28","seuilReel":28,"semantique":"CUMUL"},
   {"entete":"# of sprints (>24 km/h)","metrique":"NB_SPRINTS","seuilReel":24},
   {"entete":"vitesse max (km/h)","metrique":"VITESSE_MAX"},
   {"entete":"# of accelerations (>3 m/s2)","metrique":"NB_ACCELERATIONS"},
   {"entete":"# of decelerations (>3 m/s2)","metrique":"NB_FREINAGES"}
 ]'
) ON CONFLICT (id) DO NOTHING;
