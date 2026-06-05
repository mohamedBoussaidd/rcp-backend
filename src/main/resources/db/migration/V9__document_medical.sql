-- ============================================================
-- V9 — Documents medicaux deposes par le joueur (stockage fichier serveur)
-- Le fichier physique est hors web root (app.medical.upload-dir) ; la base
-- ne garde que les metadonnees + le chemin de stockage.
-- partage_roles : CSV des roles autorises EN PLUS du medical (toujours visible
-- par le joueur proprietaire, MEDICAL et SUPER_ADMIN).
-- ============================================================
SET search_path = public;

CREATE TABLE document_medical (
    id              uuid DEFAULT uuid_generate_v4() NOT NULL,
    joueur_id       uuid NOT NULL,
    equipe_id       uuid,
    nom_original    varchar(255) NOT NULL,
    type_mime       varchar(100) NOT NULL,
    taille_octets   bigint NOT NULL,
    chemin_stockage varchar(255) NOT NULL,
    categorie       varchar(40) NOT NULL,
    description     text,
    partage_roles   varchar(255),
    depose_par      uuid,
    date_depot      timestamp DEFAULT now() NOT NULL,
    CONSTRAINT document_medical_pkey PRIMARY KEY (id),
    CONSTRAINT document_medical_joueur_fkey FOREIGN KEY (joueur_id) REFERENCES joueur(id)      ON DELETE CASCADE,
    CONSTRAINT document_medical_equipe_fkey FOREIGN KEY (equipe_id) REFERENCES equipe(id)      ON DELETE SET NULL,
    CONSTRAINT document_medical_depose_fkey FOREIGN KEY (depose_par) REFERENCES utilisateur(id) ON DELETE SET NULL
);
CREATE INDEX idx_document_medical_joueur ON document_medical (joueur_id);
CREATE INDEX idx_document_medical_equipe ON document_medical (equipe_id);
