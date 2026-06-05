-- ============================================================
-- V12 — Suivi de blessure : journal d'évolution + protocole de retour au jeu (RTP)
--
-- blessure_note : notes datées (soins, observations) liées à une blessure.
-- rtp_etape     : étapes du protocole de reprise (A_FAIRE -> EN_COURS -> VALIDEE),
--                 avec ordre et date de validation. La progression = validées/total.
-- ============================================================
SET search_path = public;

CREATE TABLE blessure_note (
    id          uuid DEFAULT uuid_generate_v4() NOT NULL,
    blessure_id uuid NOT NULL,
    date        date NOT NULL,
    texte       text NOT NULL,
    depose_par  uuid,
    created_at  timestamp DEFAULT now() NOT NULL,
    CONSTRAINT blessure_note_pkey PRIMARY KEY (id),
    CONSTRAINT blessure_note_blessure_fkey FOREIGN KEY (blessure_id) REFERENCES blessure(id)    ON DELETE CASCADE,
    CONSTRAINT blessure_note_depose_fkey   FOREIGN KEY (depose_par)  REFERENCES utilisateur(id) ON DELETE SET NULL
);
CREATE INDEX idx_blessure_note_blessure ON blessure_note (blessure_id);

CREATE TABLE rtp_etape (
    id              uuid DEFAULT uuid_generate_v4() NOT NULL,
    blessure_id     uuid NOT NULL,
    ordre           smallint NOT NULL,
    libelle         varchar(100) NOT NULL,
    statut          varchar(20) DEFAULT 'A_FAIRE' NOT NULL,
    date_validation date,
    created_at      timestamp DEFAULT now() NOT NULL,
    CONSTRAINT rtp_etape_pkey PRIMARY KEY (id),
    CONSTRAINT rtp_etape_blessure_fkey FOREIGN KEY (blessure_id) REFERENCES blessure(id) ON DELETE CASCADE,
    CONSTRAINT rtp_etape_statut_chk CHECK (statut IN ('A_FAIRE', 'EN_COURS', 'VALIDEE'))
);
CREATE INDEX idx_rtp_etape_blessure ON rtp_etape (blessure_id);
