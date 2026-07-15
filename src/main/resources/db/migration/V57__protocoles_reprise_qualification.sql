-- ============================================================
-- V57 — Protocoles de reprise personnalisables + qualification administrative des blessures
--
-- 1) Bibliothèque de protocoles RTP par club : un modèle = des étapes types (libellé, fenêtre
--    J-début/J-fin, détail) + des critères de suggestion (CSV de codes type/zone/gravité,
--    NULL = tous — même pattern que type_document_requis.categories_age). À l'initialisation
--    d'un protocole sur une blessure, les étapes du modèle sont CLONÉES dans rtp_etape :
--    aucun lien persistant, modifier l'un ne touche jamais l'autre.
--    Le protocole 4 phases codé en dur (BlessureSuiviService.ETAPES_DEFAUT) devient un modèle
--    « Protocole standard » seedé pour chaque club existant (clubs neufs : ProtocoleModeleSeeder).
-- 2) Formulaire blessure : zone/type « autre » précisables (zone_precision / type_precision).
-- 3) Qualification administrative : AUCUNE | ARRET_MALADIE | ACCIDENT_TRAVAIL, et déclarations
--    (PDF d'arrêt / d'accident) = documents médicaux rattachés à la blessure (blessure_id).
--    Nouvelle permission blessures:qualify (MEDICAL + PRESIDENT + ADMINISTRATIF) ; l'Administratif
--    reçoit aussi blessures:read pour consulter la liste et vérifier les documents envoyés.
-- ============================================================
SET search_path = public;

-- ── 1. Bibliothèque de protocoles ────────────────────────────────────────────

CREATE TABLE protocole_modele (
    id                 uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    club_id            uuid NOT NULL REFERENCES club(id) ON DELETE CASCADE,
    nom                varchar(160) NOT NULL,
    description        text,
    actif              boolean NOT NULL DEFAULT TRUE,
    ordre              smallint NOT NULL DEFAULT 0,
    -- Critères de suggestion : CSV de codes du formulaire blessure ; NULL = s'applique à tous.
    types_blessure     text,
    zones_corporelles  text,
    gravites           text,
    created_at         timestamp NOT NULL DEFAULT now()
);

CREATE INDEX idx_protocole_modele_club ON protocole_modele(club_id);

CREATE TABLE protocole_modele_etape (
    id           uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    modele_id    uuid NOT NULL REFERENCES protocole_modele(id) ON DELETE CASCADE,
    ordre        smallint NOT NULL DEFAULT 0,
    libelle      varchar(200) NOT NULL,
    j_debut      smallint,
    j_fin        smallint,
    description  text
);

CREATE INDEX idx_protocole_modele_etape_modele ON protocole_modele_etape(modele_id);

-- Seed : « Protocole standard » (contenu identique à l'ancien ETAPES_DEFAUT) pour chaque club.
INSERT INTO protocole_modele (club_id, nom, description, actif, ordre)
SELECT c.id, 'Protocole standard',
       'Réathlétisation progressive en 4 phases (protocole par défaut).', TRUE, 1
FROM club c;

INSERT INTO protocole_modele_etape (modele_id, ordre, libelle, j_debut, j_fin, description)
SELECT m.id, e.ordre, e.libelle, e.j_debut, e.j_fin, e.description
FROM protocole_modele m
CROSS JOIN (VALUES
    (1, 'Phase 1 — Soins',                1,  5,  'Soins médicaux, glaçage, compression'),
    (2, 'Phase 2 — Reprise individuelle', 6,  12, 'Course, renforcement musculaire, proprioception'),
    (3, 'Phase 3 — Reprise collective',   13, 18, 'Entraînement avec le groupe sans contact'),
    (4, 'Phase 4 — Retour compétition',   19, 21, 'Validation médicale, retour à la compétition')
) AS e(ordre, libelle, j_debut, j_fin, description)
WHERE m.nom = 'Protocole standard';

-- ── 2. Précisions zone / type quand « autre » ───────────────────────────────

ALTER TABLE blessure ADD COLUMN zone_precision varchar(200);
ALTER TABLE blessure ADD COLUMN type_precision varchar(200);

-- ── 3. Qualification administrative + déclarations ──────────────────────────

ALTER TABLE blessure ADD COLUMN qualification_administrative varchar(30) NOT NULL DEFAULT 'AUCUNE';

-- Déclaration d'arrêt / d'accident = document médical rattaché à la blessure. SET NULL :
-- supprimer une blessure ne détruit pas un justificatif légal (il reste dans les documents).
ALTER TABLE document_medical ADD COLUMN blessure_id uuid REFERENCES blessure(id) ON DELETE SET NULL;

CREATE INDEX idx_document_medical_blessure ON document_medical(blessure_id) WHERE blessure_id IS NOT NULL;

-- Permission : rôles système Président (001), Médical (004), Administratif (005).
-- Rôles système club_id NULL → clubs existants ET futurs.
INSERT INTO role_permission (role_id, permission) VALUES
 ('a0000000-0000-0000-0000-000000000001','blessures:qualify'),
 ('a0000000-0000-0000-0000-000000000004','blessures:qualify'),
 ('a0000000-0000-0000-0000-000000000005','blessures:qualify'),
 ('a0000000-0000-0000-0000-000000000005','blessures:read')
ON CONFLICT (role_id, permission) DO NOTHING;
