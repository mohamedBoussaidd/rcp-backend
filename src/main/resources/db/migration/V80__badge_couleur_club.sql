-- P2 du système de badges : personnalisation des couleurs.
--   badge_palette_ton.personnalise : le super-admin a-t-il RÉELLEMENT édité ce ton ? Le front
--       n'injecte que les tons personnalisés (les autres gardent le défaut theme-aware de :root),
--       ce qui préserve le rendu clair/sombre pour tout ce qui n'a pas été touché.
--   badge_couleur_club : surcharge de couleur PAR CLUB, au niveau des 6 tons uniquement (les clubs
--       ne touchent qu'aux badges système informatifs ; les tags gardent leur couleur explicite).

ALTER TABLE badge_palette_ton ADD COLUMN personnalise BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE badge_couleur_club (
  club_id    UUID         NOT NULL REFERENCES club(id) ON DELETE CASCADE,
  ton        VARCHAR(20)  NOT NULL,   -- NEUTRAL / INFO / SUCCESS / WARNING / DANGER / BRAND
  couleur_bg VARCHAR(120) NOT NULL,
  couleur_fg VARCHAR(30)  NOT NULL,
  updated_at TIMESTAMP    NOT NULL DEFAULT NOW(),
  PRIMARY KEY (club_id, ton)
);
