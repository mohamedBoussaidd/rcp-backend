-- Système de badges unifié.
--   badge_palette_ton  : les 6 tons de couleur (paire {fond, texte}), éditables par le super-admin.
--   badge_definition   : tous les badges. Deux portées :
--       SYSTEME    = badge placé par le CODE (clé stable référencée dans les templates). Le
--                    super-admin édite label/icône/ton/couleur, l'active/désactive, sans le supprimer.
--                    Couleur résolue par le TON (donc réajustable par le club).
--       PLATEFORME = tag créé par le super-admin, ASSIGNÉ à des entités (exercice/séance/joueur),
--                    visible par tous les clubs. Couleur = paire {fond, texte} EXPLICITE et fixe
--                    (couleur_bg/couleur_fg), jamais recolorée par un club.
-- Les clubs ne créent aucun badge ; ils ne surchargent que la couleur des 6 tons (cf. V80).

CREATE TABLE badge_palette_ton (
  ton        VARCHAR(20) PRIMARY KEY,    -- NEUTRAL / INFO / SUCCESS / WARNING / DANGER / BRAND
  libelle    VARCHAR(60)  NOT NULL,
  couleur_bg VARCHAR(120) NOT NULL,      -- CSS : hex OU dégradé (le ton BRAND porte un gradient)
  couleur_fg VARCHAR(30)  NOT NULL,
  updated_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Valeurs par défaut alignées sur les tokens du design system (styles.scss) : le front garde ces
-- mêmes défauts en :root (theme-aware) ; ces lignes servent de point de départ éditable au super-admin.
INSERT INTO badge_palette_ton (ton, libelle, couleur_bg, couleur_fg) VALUES
  ('NEUTRAL', 'Neutre',       '#E5E9EF', '#1E293B'),
  ('INFO',    'Information',   '#EFF6FF', '#1D4ED8'),
  ('SUCCESS', 'Validé / vert', '#ECFDF5', '#15803D'),
  ('WARNING', 'Attention',     '#FFFBEB', '#B45309'),
  ('DANGER',  'Alerte',        '#FEF2F2', '#B91C1C'),
  ('BRAND',   'IA / marque',   'linear-gradient(135deg, #7c3aed 0%, #4f46e5 100%)', '#FFFFFF')
ON CONFLICT (ton) DO NOTHING;

CREATE TABLE badge_definition (
  id         UUID PRIMARY KEY,
  cle        VARCHAR(60)  NOT NULL UNIQUE,   -- clé stable ; référencée par le code pour les SYSTEME
  label      VARCHAR(60)  NOT NULL,
  icone      VARCHAR(60),                    -- nom mat-icon (nullable)
  ton        VARCHAR(20)  NOT NULL DEFAULT 'NEUTRAL',
  mode       VARCHAR(10)  NOT NULL DEFAULT 'INLINE',    -- INLINE / CORNER
  portee     VARCHAR(20)  NOT NULL DEFAULT 'SYSTEME',   -- SYSTEME / PLATEFORME
  couleur_bg VARCHAR(120),                   -- override explicite ({fond} ; obligatoire pour les tags)
  couleur_fg VARCHAR(30),                    -- override explicite ({texte})
  tooltip    VARCHAR(160),
  actif      BOOLEAN      NOT NULL DEFAULT TRUE,
  ordre      INT          NOT NULL DEFAULT 0,
  created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_badge_definition_portee ON badge_definition (portee, actif);

-- Vocabulaire système initial. Les clés sont figées (le code s'y réfère). Tous les badges système
-- suivent leur TON (couleur explicite NULL) → donc réajustables par le club via la palette des tons.
-- Le ton BRAND porte le dégradé IA (cf. badge_palette_ton) + son ombre côté CSS.
INSERT INTO badge_definition (id, cle, label, icone, ton, mode, portee, couleur_bg, couleur_fg, tooltip, ordre) VALUES
  ('11111111-1111-1111-1111-111111111101', 'ia',       'IA',       'auto_awesome', 'BRAND',   'CORNER', 'SYSTEME', NULL, NULL, 'Proposé / généré par l''IA', 10),
  ('11111111-1111-1111-1111-111111111102', 'schema',   'Schéma',   'schema',       'SUCCESS', 'INLINE', 'SYSTEME', NULL, NULL, 'Contenu doté d''un schéma tactique', 20),
  ('11111111-1111-1111-1111-111111111103', 'global',   'Global',   'public',       'INFO',    'INLINE', 'SYSTEME', NULL, NULL, 'Contenu global, commun à tous les clubs', 30),
  ('11111111-1111-1111-1111-111111111104', 'blessure', 'Blessé',   'healing',      'DANGER',  'INLINE', 'SYSTEME', NULL, NULL, 'Joueur blessé', 40),
  ('11111111-1111-1111-1111-111111111105', 'gene',     'Gêne',     'warning',      'WARNING', 'INLINE', 'SYSTEME', NULL, NULL, 'Joueur avec une gêne signalée', 50),
  ('11111111-1111-1111-1111-111111111106', 'present',  'Présent',  'check',        'SUCCESS', 'INLINE', 'SYSTEME', NULL, NULL, 'Présent', 60),
  ('11111111-1111-1111-1111-111111111107', 'absent',   'Absent',   'block',        'DANGER',  'INLINE', 'SYSTEME', NULL, NULL, 'Absent', 70)
ON CONFLICT (cle) DO NOTHING;
