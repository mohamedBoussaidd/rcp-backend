-- ============================================================
-- V62 — Import de séance/exercice depuis une photo (module `import_photo_ia`)
--
-- L'entraîneur photographie sa séance papier ; l'IA (vision) extrait le contenu
-- (champs simples + enrichis du chantier séance_avancée + schéma tactique) et
-- pré-remplit les formulaires. L'entraîneur corrige puis enregistre (~80 % auto).
--
--  · parametre_ia : clé/valeur globale ÉDITABLE par le super-admin (prompt vision
--    `prompt_import_photo`, quota par défaut `quota_import_photo_defaut`) avec
--    HISTORIQUE des versions (restauration possible). Prompt par défaut en dur
--    dans le code en fallback si la clé est absente.
--  · club_parametre : surcharges par club (ex. quota d'appels/jour relevé pour
--    un club précis par le super-admin).
--  · import_photo_journal : 1 ligne par appel — sert au QUOTA (comptage par
--    club et par jour), à l'audit de coût, et conserve la photo d'origine
--    (chemin disque ./data/import-photos), rattachable à l'exercice créé.
-- ============================================================
SET search_path = public;

CREATE TABLE parametre_ia (
    cle        varchar(60) PRIMARY KEY,
    valeur     text NOT NULL,
    updated_at timestamp NOT NULL DEFAULT now(),
    updated_by uuid
);

CREATE TABLE parametre_ia_historique (
    id         uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    cle        varchar(60) NOT NULL,
    valeur     text NOT NULL,
    created_at timestamp NOT NULL DEFAULT now(),
    created_by uuid
);

CREATE INDEX idx_parametre_ia_hist_cle ON parametre_ia_historique(cle, created_at DESC);

-- Quota par défaut : 20 appels / club / jour (décision user 2026-07-20).
-- Le prompt vision par défaut est seedé PAR LE CODE au premier démarrage
-- (ParametreIaService) pour rester la source unique du texte.
INSERT INTO parametre_ia (cle, valeur) VALUES ('quota_import_photo_defaut', '20');

-- Surcharges par club (clé libre : `quota_import_photo`, …).
CREATE TABLE club_parametre (
    club_id uuid NOT NULL REFERENCES club(id) ON DELETE CASCADE,
    cle     varchar(60) NOT NULL,
    valeur  text NOT NULL,
    PRIMARY KEY (club_id, cle)
);

CREATE TABLE import_photo_journal (
    id             uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    club_id        uuid NOT NULL REFERENCES club(id) ON DELETE CASCADE,
    utilisateur_id uuid,
    created_at     timestamp NOT NULL DEFAULT now(),
    statut         varchar(20) NOT NULL DEFAULT 'OK',   -- OK | ERREUR | ILLISIBLE
    photo_path     varchar(255),
    message        varchar(500)
);

CREATE INDEX idx_import_photo_journal_club ON import_photo_journal(club_id, created_at DESC);

-- Rattachement de la photo d'origine à l'exercice créé depuis l'import.
ALTER TABLE exercice
    ADD COLUMN photo_import_id uuid REFERENCES import_photo_journal(id) ON DELETE SET NULL;

-- Module `import_photo_ia` : pack Complet uniquement (add-on ailleurs via club_module).
INSERT INTO pack_module (pack_code, module_code) VALUES
 ('complet', 'import_photo_ia')
ON CONFLICT DO NOTHING;

-- Permission d'usage : rôles qui préparent les séances (comme seance_avancee:access).
INSERT INTO role_permission (role_id, permission) VALUES
 ('a0000000-0000-0000-0000-000000000001', 'import_photo:use'),
 ('a0000000-0000-0000-0000-000000000002', 'import_photo:use'),
 ('a0000000-0000-0000-0000-000000000003', 'import_photo:use'),
 ('a0000000-0000-0000-0000-000000000006', 'import_photo:use')
ON CONFLICT (role_id, permission) DO NOTHING;
