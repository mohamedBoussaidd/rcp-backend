-- ============================================================================
-- V42 — Packs commerciaux & activation des modules par club
--
-- Couche « produit / abonnement » : un club possède un PACK (bundle de modules),
-- que le super-admin peut compléter/retirer module par module (surcharges).
-- L'activation d'un module conditionne l'accès aux permissions correspondantes
-- (voir FeatureModule + PermissionResolver côté back) : module off = endpoints 403
-- et écrans masqués, SANS suppression de données.
--
-- Prix modélisé dès maintenant (prix_mensuel), éditable par le super-admin, NULL
-- tant qu'il n'est pas défini. Le paiement viendra plus tard.
-- ============================================================================

-- Catalogue des packs (prédéfinis seed + packs custom créés par le super-admin)
CREATE TABLE pack (
    code         VARCHAR(40)  PRIMARY KEY,
    libelle      VARCHAR(120) NOT NULL,
    description  TEXT,
    prix_mensuel NUMERIC(8,2),                      -- éditable super-admin, NULL = non défini
    ordre        INT          NOT NULL DEFAULT 0,
    actif        BOOLEAN      NOT NULL DEFAULT TRUE,
    predefini    BOOLEAN      NOT NULL DEFAULT FALSE, -- packs seed : non supprimables
    cree_le      TIMESTAMP    NOT NULL DEFAULT now()
);

-- Modules inclus dans un pack (codes libres, validés contre l'enum FeatureModule côté back)
CREATE TABLE pack_module (
    pack_code   VARCHAR(40) NOT NULL REFERENCES pack(code) ON DELETE CASCADE,
    module_code VARCHAR(40) NOT NULL,
    PRIMARY KEY (pack_code, module_code)
);

-- Surcharges par club, PAR-DESSUS le pack : actif=true (add-on) / actif=false (retrait explicite).
-- Les surcharges l'emportent toujours sur le pack (permet la propagation d'une édition de pack
-- tout en respectant un réglage manuel spécifique à un club).
CREATE TABLE club_module (
    club_id     UUID        NOT NULL REFERENCES club(id) ON DELETE CASCADE,
    module_code VARCHAR(40) NOT NULL,
    actif       BOOLEAN     NOT NULL,
    maj_le      TIMESTAMP   NOT NULL DEFAULT now(),
    PRIMARY KEY (club_id, module_code)
);

-- Rattachement pack ↔ club
ALTER TABLE club ADD COLUMN pack_code VARCHAR(40) REFERENCES pack(code);

-- ── Seed des 4 packs (proposition A : échelle progressive) ──────────────────
INSERT INTO pack (code, libelle, description, ordre, predefini) VALUES
 ('essentiel',   'Essentiel',
  'Planning, effectif, présence, matchs et espace joueur. Idéal pour un club amateur.', 1, TRUE),
 ('prepa',       'Prépa',
  'Essentiel + suivi de la charge (wellness / RPE), préparation physique et pesées.', 2, TRUE),
 ('performance', 'Performance',
  'Prépa + GPS, tactique et diaporama. Pour les structures équipées.', 3, TRUE),
 ('complet',     'Complet',
  'Toutes les fonctionnalités, dont le module médical et les notifications.', 4, TRUE);

-- Modules par pack (cumulatif ; les modules socle planning/effectif/administration
-- sont TOUJOURS actifs et ne sont donc pas listés ici).
INSERT INTO pack_module (pack_code, module_code) VALUES
 -- Essentiel
 ('essentiel','presence'), ('essentiel','match'), ('essentiel','pwa_joueur'),
 -- Prépa
 ('prepa','presence'), ('prepa','match'), ('prepa','pwa_joueur'),
 ('prepa','wellness'), ('prepa','prepa_physique'), ('prepa','pesees'),
 -- Performance
 ('performance','presence'), ('performance','match'), ('performance','pwa_joueur'),
 ('performance','wellness'), ('performance','prepa_physique'), ('performance','pesees'),
 ('performance','gps'), ('performance','tactique'), ('performance','diaporama'),
 -- Complet
 ('complet','presence'), ('complet','match'), ('complet','pwa_joueur'),
 ('complet','wellness'), ('complet','prepa_physique'), ('complet','pesees'),
 ('complet','gps'), ('complet','tactique'), ('complet','diaporama'),
 ('complet','medical'), ('complet','notifications');

-- ── Grandfather : les clubs DÉJÀ existants passent en Complet (aucune régression) ──
UPDATE club SET pack_code = 'complet' WHERE pack_code IS NULL;
