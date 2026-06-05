-- ============================================================
-- V11 — Blessure enrichie : statut clinique + date de retour prévue
--
-- statut : cycle de vie clinique (INDISPONIBLE -> EN_REPRISE -> RETABLI).
-- date_retour_prevue : estimation (date_retour_effectif reste le retour réel).
-- Backfill : les blessures déjà revenues (retour effectif passé) -> RETABLI,
-- les autres -> INDISPONIBLE.
-- ============================================================
SET search_path = public;

ALTER TABLE blessure ADD COLUMN statut varchar(20);
ALTER TABLE blessure ADD COLUMN date_retour_prevue date;

UPDATE blessure SET statut = CASE
    WHEN date_retour_effectif IS NOT NULL AND date_retour_effectif <= CURRENT_DATE THEN 'RETABLI'
    ELSE 'INDISPONIBLE'
END;

ALTER TABLE blessure ALTER COLUMN statut SET DEFAULT 'INDISPONIBLE';
ALTER TABLE blessure ALTER COLUMN statut SET NOT NULL;
ALTER TABLE blessure ADD CONSTRAINT blessure_statut_check
    CHECK (statut IN ('INDISPONIBLE', 'EN_REPRISE', 'RETABLI'));
