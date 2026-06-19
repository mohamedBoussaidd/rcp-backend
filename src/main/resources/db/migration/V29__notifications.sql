-- ============================================================
-- V29 — Système de notifications (in-app + Web Push)
--
-- 6 tables :
--   notification        : notification/message délivré à UN destinataire (fan-out par producteur)
--   push_subscription   : abonnement Web Push (par device/navigateur d'un utilisateur)
--   notif_config_equipe : config par équipe — seuils, heures de digest, rappels (1 ligne/équipe)
--   notif_routage       : quels rôles staff reçoivent quel type de notif (par équipe)
--   notif_preference    : préférence par destinataire × type (actif + verrou staff)
--   notif_droit_envoi   : droit d'un joueur d'émettre des notifs (niveau ÉQUIPE / CIBLE)
-- ============================================================
SET search_path = public;

-- ── Notification délivrée (1 ligne = 1 destinataire) ───────────────────────
CREATE TABLE notification (
    id                    uuid DEFAULT uuid_generate_v4() NOT NULL,
    equipe_id             uuid NOT NULL,
    destinataire_user_id  uuid NOT NULL,              -- utilisateur qui reçoit
    sujet_joueur_id       uuid,                       -- joueur concerné (contexte alerte), nullable
    type                  varchar(30) NOT NULL,       -- RAPPEL_WELLNESS, ALERTE_CHARGE, MESSAGE_STAFF, DIGEST…
    emetteur_type         varchar(20) NOT NULL DEFAULT 'SYSTEME',  -- SYSTEME | UTILISATEUR
    emetteur_user_id      uuid,                       -- si émetteur humain
    titre                 varchar(160) NOT NULL,
    corps                 text,
    lien                  varchar(255),               -- deep-link vers l'écran concerné
    priorite              varchar(10) NOT NULL DEFAULT 'NORMALE',  -- NORMALE | URGENTE
    thread_id             uuid,                       -- regroupement chat (évolutif → réponses)
    repondable            boolean NOT NULL DEFAULT false,
    lu                    boolean NOT NULL DEFAULT false,
    lu_at                 timestamp,
    created_at            timestamp DEFAULT now() NOT NULL,
    CONSTRAINT notification_pkey PRIMARY KEY (id),
    CONSTRAINT notification_equipe_fkey FOREIGN KEY (equipe_id) REFERENCES equipe(id) ON DELETE CASCADE,
    CONSTRAINT notification_dest_fkey FOREIGN KEY (destinataire_user_id) REFERENCES utilisateur(id) ON DELETE CASCADE,
    CONSTRAINT notification_sujet_fkey FOREIGN KEY (sujet_joueur_id) REFERENCES joueur(id) ON DELETE SET NULL,
    CONSTRAINT notification_emetteur_fkey FOREIGN KEY (emetteur_user_id) REFERENCES utilisateur(id) ON DELETE SET NULL
);
CREATE INDEX idx_notif_dest_nonlu ON notification (destinataire_user_id, lu);
CREATE INDEX idx_notif_dest_date ON notification (destinataire_user_id, created_at DESC);
CREATE INDEX idx_notif_thread ON notification (thread_id);

-- ── Abonnement Web Push (un par device) ────────────────────────────────────
CREATE TABLE push_subscription (
    id          uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id     uuid NOT NULL,
    endpoint    text NOT NULL,
    p256dh      varchar(255) NOT NULL,
    auth        varchar(255) NOT NULL,
    user_agent  varchar(255),
    created_at  timestamp DEFAULT now() NOT NULL,
    CONSTRAINT push_subscription_pkey PRIMARY KEY (id),
    CONSTRAINT push_subscription_endpoint_uq UNIQUE (endpoint),
    CONSTRAINT push_subscription_user_fkey FOREIGN KEY (user_id) REFERENCES utilisateur(id) ON DELETE CASCADE
);
CREATE INDEX idx_push_user ON push_subscription (user_id);

-- ── Config de notification par équipe (seuils + digests + rappels) ─────────
CREATE TABLE notif_config_equipe (
    id                      uuid DEFAULT uuid_generate_v4() NOT NULL,
    equipe_id               uuid NOT NULL,
    -- Seuils de charge / état
    seuil_acwr_haut         numeric(4,2) NOT NULL DEFAULT 1.50,
    seuil_acwr_bas          numeric(4,2) NOT NULL DEFAULT 0.80,
    seuil_readiness_min     numeric(5,1) NOT NULL DEFAULT 50.0,   -- alerte si readiness < seuil
    -- Seuils wellness (Hooper, 5 items sur 1..5 ; convention app : 1 = bon … 5 = mauvais
    -- pour TOUS les items). Alerte si valeur >= seuil.
    seuil_wellness_fatigue   smallint NOT NULL DEFAULT 4,
    seuil_wellness_douleur   smallint NOT NULL DEFAULT 4,
    seuil_wellness_stress    smallint NOT NULL DEFAULT 4,
    seuil_wellness_sommeil   smallint NOT NULL DEFAULT 4,
    seuil_wellness_humeur    smallint NOT NULL DEFAULT 4,
    -- Seuils de poids (kg)
    seuil_poids_court       numeric(4,1) NOT NULL DEFAULT 1.0,    -- variation court terme
    seuil_poids_moyen       numeric(4,1) NOT NULL DEFAULT 3.0,    -- variation moyen terme
    -- Seuil de complétion (% de joueurs ayant saisi)
    seuil_completion_min    smallint NOT NULL DEFAULT 70,
    -- Digests
    digest_actif            boolean NOT NULL DEFAULT true,
    digest_matin_heure      time NOT NULL DEFAULT '08:00',
    digest_soir_heure       time NOT NULL DEFAULT '19:00',
    -- Rappels joueur
    rappel_wellness_actif   boolean NOT NULL DEFAULT true,
    rappel_wellness_heure   time NOT NULL DEFAULT '08:00',
    rappel_rpe_actif        boolean NOT NULL DEFAULT true,
    rappel_rpe_delai_heures smallint NOT NULL DEFAULT 3,          -- rappel RPE X h après la séance
    rappel_poids_actif      boolean NOT NULL DEFAULT false,
    rappel_seance_actif     boolean NOT NULL DEFAULT true,
    created_at              timestamp DEFAULT now() NOT NULL,
    updated_at              timestamp DEFAULT now() NOT NULL,
    CONSTRAINT notif_config_equipe_pkey PRIMARY KEY (id),
    CONSTRAINT notif_config_equipe_uq UNIQUE (equipe_id),
    CONSTRAINT notif_config_equipe_fkey FOREIGN KEY (equipe_id) REFERENCES equipe(id) ON DELETE CASCADE
);

-- ── Routage : quels rôles reçoivent quel type (par équipe) ─────────────────
CREATE TABLE notif_routage (
    id          uuid DEFAULT uuid_generate_v4() NOT NULL,
    equipe_id   uuid NOT NULL,
    type        varchar(30) NOT NULL,                 -- type de notification
    roles       varchar(160) NOT NULL DEFAULT '',     -- rôles destinataires, CSV (ex. 'PREPARATEUR,MEDICAL')
    actif       boolean NOT NULL DEFAULT true,
    CONSTRAINT notif_routage_pkey PRIMARY KEY (id),
    CONSTRAINT notif_routage_uq UNIQUE (equipe_id, type),
    CONSTRAINT notif_routage_equipe_fkey FOREIGN KEY (equipe_id) REFERENCES equipe(id) ON DELETE CASCADE
);
CREATE INDEX idx_notif_routage_equipe ON notif_routage (equipe_id);

-- ── Préférence par destinataire × type (actif + verrou staff) ──────────────
CREATE TABLE notif_preference (
    id                   uuid DEFAULT uuid_generate_v4() NOT NULL,
    user_id              uuid NOT NULL,               -- destinataire concerné
    type                 varchar(30) NOT NULL,
    actif                boolean NOT NULL DEFAULT true,
    verrouille_par_staff boolean NOT NULL DEFAULT false,  -- true => le destinataire ne peut pas modifier
    CONSTRAINT notif_preference_pkey PRIMARY KEY (id),
    CONSTRAINT notif_preference_uq UNIQUE (user_id, type),
    CONSTRAINT notif_preference_user_fkey FOREIGN KEY (user_id) REFERENCES utilisateur(id) ON DELETE CASCADE
);
CREATE INDEX idx_notif_pref_user ON notif_preference (user_id);

-- ── Droit d'envoi d'un joueur (émetteur joueur) ────────────────────────────
CREATE TABLE notif_droit_envoi (
    id         uuid DEFAULT uuid_generate_v4() NOT NULL,
    joueur_id  uuid NOT NULL,
    equipe_id  uuid NOT NULL,
    niveau     varchar(10) NOT NULL DEFAULT 'AUCUN', -- AUCUN | EQUIPE | CIBLE
    CONSTRAINT notif_droit_envoi_pkey PRIMARY KEY (id),
    CONSTRAINT notif_droit_envoi_uq UNIQUE (joueur_id),
    CONSTRAINT notif_droit_envoi_joueur_fkey FOREIGN KEY (joueur_id) REFERENCES joueur(id) ON DELETE CASCADE,
    CONSTRAINT notif_droit_envoi_equipe_fkey FOREIGN KEY (equipe_id) REFERENCES equipe(id) ON DELETE CASCADE
);
CREATE INDEX idx_notif_droit_equipe ON notif_droit_envoi (equipe_id);
