-- ============================================================
-- V86 — Réglage de l'alerte « dérives de charge » dans la config de notification par équipe.
--
-- La carte add-on `assistant_derives` peut émettre une surveillance hebdomadaire (staff prépa) :
-- notification in-app + push si des dérives lentes franchissent le seuil. Le déclenchement est
-- paramétrable comme les autres alertes, via une colonne dédiée (défaut ON).
-- ============================================================
SET search_path = public;

ALTER TABLE notif_config_equipe
    ADD COLUMN IF NOT EXISTS derives_alerte_active boolean NOT NULL DEFAULT true;
