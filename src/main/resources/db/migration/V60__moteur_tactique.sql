-- ============================================================
-- V60 — Moteur tactique (module `moteur_tactique`)
--
-- Jeu de règles de positionnement collectif : pour chaque (système, phase, zone de
-- ballon 4×3) une « posture » = position relative [0..1] des 11 postes (slots).
-- regles_json est OPAQUE côté Java (comme schema_json) — le front en possède la
-- sémantique (interpolation gaussienne, miroir adverse, ajustements par slot).
--
-- type NOUS = identité de l'équipe (1 seul par équipe+système, contrôle applicatif) ;
-- type ADVERSAIRE = profils nommés réutilisables (« Bloc bas », « FC X »…),
-- attachables à un match pour la préparation (match_prepa.profil_adverse_id,
-- référence — pas copie : le profil se prépare jusqu'au match).
-- ============================================================
SET search_path = public;

CREATE TABLE regle_tactique (
    id          uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    equipe_id   uuid NOT NULL REFERENCES equipe(id) ON DELETE CASCADE,
    type        varchar(12) NOT NULL CHECK (type IN ('NOUS', 'ADVERSAIRE')),
    nom         varchar(120) NOT NULL,
    systeme     varchar(20) NOT NULL,
    regles_json text NOT NULL,
    cree_par    uuid,
    created_at  timestamp NOT NULL DEFAULT now(),
    updated_at  timestamp NOT NULL DEFAULT now()
);

CREATE INDEX idx_regle_tactique_equipe ON regle_tactique(equipe_id);

-- Brique 5 : profil adverse préparé pour un match (référence, nullable).
ALTER TABLE match_prepa
    ADD COLUMN profil_adverse_id uuid REFERENCES regle_tactique(id) ON DELETE SET NULL;

-- Module `moteur_tactique` : pack Complet uniquement (add-on ailleurs via club_module).
INSERT INTO pack_module (pack_code, module_code) VALUES
 ('complet', 'moteur_tactique')
ON CONFLICT DO NOTHING;

-- Lecture : même distribution que plandejeu:read (Président, Entraîneur, Préparateur,
-- Médical, Entraîneur en chef). Écriture : comme plandejeu:write (Président, Entraîneur,
-- Entraîneur en chef). Réattribuable par club via la matrice Rôles & accès.
INSERT INTO role_permission (role_id, permission) VALUES
 ('a0000000-0000-0000-0000-000000000001', 'regles_tactiques:read'),
 ('a0000000-0000-0000-0000-000000000002', 'regles_tactiques:read'),
 ('a0000000-0000-0000-0000-000000000003', 'regles_tactiques:read'),
 ('a0000000-0000-0000-0000-000000000004', 'regles_tactiques:read'),
 ('a0000000-0000-0000-0000-000000000006', 'regles_tactiques:read'),
 ('a0000000-0000-0000-0000-000000000001', 'regles_tactiques:write'),
 ('a0000000-0000-0000-0000-000000000002', 'regles_tactiques:write'),
 ('a0000000-0000-0000-0000-000000000006', 'regles_tactiques:write')
ON CONFLICT (role_id, permission) DO NOTHING;
