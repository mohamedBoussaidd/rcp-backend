-- ============================================================
-- V59 — Contrats & fiches de paye (module `contrats`)
--
-- Contrat : niveau CLUB, rattaché à la fiche personne (joueur OU staff) ; statut
-- actif/expiré DÉRIVÉ des dates (jamais stocké) ; PDF signé joint optionnel ; PAS de
-- salaire (décision user). Vue équipe = filtre par effectif saison, côté front.
--
-- Bulletin de paie : transmission pure — une ligne par (club, personne, mois), trois
-- jalons : depose_le → notifie_le (bouton « Distribuer » = notification à la personne)
-- → premier_telechargement_le (timbré au 1er téléchargement par la personne).
--
-- Confidentialité : /api/contrats/** et /api/bulletins-paie/** gardés par la nouvelle
-- permission contrats:manage (Président + Administratif) ; la personne consulte les
-- SIENS via /api/membre/** (self-scope). Module `contrats` : pack Complet + add-on.
-- ============================================================
SET search_path = public;

CREATE TABLE contrat (
    id               uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    club_id          uuid NOT NULL REFERENCES club(id) ON DELETE CASCADE,
    joueur_id        uuid NOT NULL REFERENCES joueur(id) ON DELETE CASCADE,
    type_contrat     varchar(60) NOT NULL,
    date_debut       date NOT NULL,
    date_fin         date,
    nom_original     varchar(255),
    type_mime        varchar(100),
    taille_octets    bigint,
    chemin_stockage  varchar(255),
    notes            text,
    cree_par         uuid,
    created_at       timestamp NOT NULL DEFAULT now()
);

CREATE INDEX idx_contrat_club ON contrat(club_id);
CREATE INDEX idx_contrat_joueur ON contrat(joueur_id);
CREATE INDEX idx_contrat_date_fin ON contrat(date_fin) WHERE date_fin IS NOT NULL;

CREATE TABLE bulletin_paie (
    id                         uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    club_id                    uuid NOT NULL REFERENCES club(id) ON DELETE CASCADE,
    joueur_id                  uuid NOT NULL REFERENCES joueur(id) ON DELETE CASCADE,
    periode                    date NOT NULL,   -- 1er jour du mois
    nom_original               varchar(255) NOT NULL,
    type_mime                  varchar(100) NOT NULL,
    taille_octets              bigint NOT NULL,
    chemin_stockage            varchar(255) NOT NULL,
    depose_par                 uuid,
    depose_le                  timestamp NOT NULL DEFAULT now(),
    notifie_le                 timestamp,
    premier_telechargement_le  timestamp,
    UNIQUE (club_id, joueur_id, periode)
);

CREATE INDEX idx_bulletin_club_periode ON bulletin_paie(club_id, periode);
CREATE INDEX idx_bulletin_joueur ON bulletin_paie(joueur_id);

-- Module `contrats` : pack Complet uniquement (add-on ailleurs via club_module).
INSERT INTO pack_module (pack_code, module_code) VALUES
 ('complet', 'contrats')
ON CONFLICT DO NOTHING;

-- Permission de gestion : Président (001) + Administratif (005), rôles système → tous clubs.
INSERT INTO role_permission (role_id, permission) VALUES
 ('a0000000-0000-0000-0000-000000000001', 'contrats:manage'),
 ('a0000000-0000-0000-0000-000000000005', 'contrats:manage')
ON CONFLICT (role_id, permission) DO NOTHING;
