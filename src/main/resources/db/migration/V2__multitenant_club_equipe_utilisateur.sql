-- ============================================================
-- V2 — Multi-tenant : Club > Equipe > Utilisateur + scoping equipe_id
-- Hierarchie : SUPER_ADMIN > CLUB (president) > EQUIPE (max 3) > staff/joueurs
-- equipe_id laisse NULLABLE en v1 (scoping applique au niveau applicatif).
-- ============================================================
SET search_path = public;

-- ── Club (racine d'un club, gere par un president) ──
CREATE TABLE club (
    id            uuid DEFAULT uuid_generate_v4() NOT NULL,
    nom           varchar(150) NOT NULL,
    logo          varchar(255),
    date_creation date DEFAULT CURRENT_DATE NOT NULL,
    president_id  uuid,                       -- FK ajoutee plus bas (cyclique avec utilisateur)
    created_at    timestamp DEFAULT now() NOT NULL,
    CONSTRAINT club_pkey PRIMARY KEY (id)
);

-- ── Equipe (max 3 par club, controle cote service) ──
CREATE TABLE equipe (
    id         uuid DEFAULT uuid_generate_v4() NOT NULL,
    nom        varchar(100) NOT NULL,
    categorie  varchar(50),                   -- ex: Seniors, U19, Equipe B
    club_id    uuid NOT NULL,
    created_at timestamp DEFAULT now() NOT NULL,
    CONSTRAINT equipe_pkey PRIMARY KEY (id),
    CONSTRAINT equipe_club_fkey FOREIGN KEY (club_id) REFERENCES club(id) ON DELETE CASCADE
);
CREATE INDEX idx_equipe_club ON equipe (club_id);

-- ── Utilisateur (compte connectable, 1 role par personne) ──
CREATE TABLE utilisateur (
    id           uuid DEFAULT uuid_generate_v4() NOT NULL,
    email        varchar(180) NOT NULL,
    mot_de_passe varchar(255) NOT NULL,       -- hash BCrypt
    nom          varchar(100),
    prenom       varchar(100),
    role         varchar(20) NOT NULL,
    specialite   varchar(30),                 -- MEDICAL: kine/medecin/osteo ; ADMINISTRATIF: secretaire/tresorier
    club_id      uuid,                         -- NULL pour SUPER_ADMIN
    equipe_id    uuid,                         -- NULL pour SUPER_ADMIN et PRESIDENT
    joueur_id    uuid,                         -- lien optionnel vers la fiche sportive (role JOUEUR)
    actif        boolean DEFAULT true NOT NULL,
    created_at   timestamp DEFAULT now() NOT NULL,
    CONSTRAINT utilisateur_pkey PRIMARY KEY (id),
    CONSTRAINT utilisateur_email_key UNIQUE (email),
    CONSTRAINT utilisateur_role_check CHECK (role IN
        ('SUPER_ADMIN','PRESIDENT','ENTRAINEUR','PREPARATEUR','MEDICAL','ADMINISTRATIF','JOUEUR')),
    CONSTRAINT utilisateur_club_fkey   FOREIGN KEY (club_id)   REFERENCES club(id)    ON DELETE CASCADE,
    CONSTRAINT utilisateur_equipe_fkey FOREIGN KEY (equipe_id) REFERENCES equipe(id)  ON DELETE SET NULL,
    CONSTRAINT utilisateur_joueur_fkey FOREIGN KEY (joueur_id) REFERENCES joueur(id)  ON DELETE SET NULL
);
CREATE INDEX idx_utilisateur_club   ON utilisateur (club_id);
CREATE INDEX idx_utilisateur_equipe ON utilisateur (equipe_id);

-- FK president (cyclique : on l'ajoute apres la creation d'utilisateur)
ALTER TABLE club
    ADD CONSTRAINT club_president_fkey FOREIGN KEY (president_id) REFERENCES utilisateur(id) ON DELETE SET NULL;

-- ── Scoping : equipe_id sur les tables de donnees existantes ──
ALTER TABLE joueur           ADD COLUMN equipe_id uuid;
ALTER TABLE seance           ADD COLUMN equipe_id uuid;
ALTER TABLE blessure         ADD COLUMN equipe_id uuid;
ALTER TABLE donnee_gps       ADD COLUMN equipe_id uuid;
ALTER TABLE historique_poids ADD COLUMN equipe_id uuid;
ALTER TABLE baseline_joueur  ADD COLUMN equipe_id uuid;
ALTER TABLE agregat_joueur   ADD COLUMN equipe_id uuid;
ALTER TABLE recommandation   ADD COLUMN equipe_id uuid;

ALTER TABLE joueur           ADD CONSTRAINT joueur_equipe_fkey           FOREIGN KEY (equipe_id) REFERENCES equipe(id) ON DELETE CASCADE;
ALTER TABLE seance           ADD CONSTRAINT seance_equipe_fkey           FOREIGN KEY (equipe_id) REFERENCES equipe(id) ON DELETE CASCADE;
ALTER TABLE blessure         ADD CONSTRAINT blessure_equipe_fkey         FOREIGN KEY (equipe_id) REFERENCES equipe(id) ON DELETE CASCADE;
ALTER TABLE donnee_gps       ADD CONSTRAINT donnee_gps_equipe_fkey       FOREIGN KEY (equipe_id) REFERENCES equipe(id) ON DELETE CASCADE;
ALTER TABLE historique_poids ADD CONSTRAINT historique_poids_equipe_fkey FOREIGN KEY (equipe_id) REFERENCES equipe(id) ON DELETE CASCADE;
ALTER TABLE baseline_joueur  ADD CONSTRAINT baseline_joueur_equipe_fkey  FOREIGN KEY (equipe_id) REFERENCES equipe(id) ON DELETE CASCADE;
ALTER TABLE agregat_joueur   ADD CONSTRAINT agregat_joueur_equipe_fkey   FOREIGN KEY (equipe_id) REFERENCES equipe(id) ON DELETE CASCADE;
ALTER TABLE recommandation   ADD CONSTRAINT recommandation_equipe_fkey   FOREIGN KEY (equipe_id) REFERENCES equipe(id) ON DELETE CASCADE;

CREATE INDEX idx_joueur_equipe ON joueur (equipe_id);
CREATE INDEX idx_seance_equipe ON seance (equipe_id);
