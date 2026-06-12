-- ============================================================
-- V25 — Aligner presence.statut sur le reste du modèle (varchar)
-- La colonne était un type enum Postgres (statut_presence), ce qui
-- provoquait un cast implicite varchar -> enum refusé par Postgres à
-- l'INSERT (Hibernate envoie un varchar via @Enumerated(EnumType.STRING)).
-- On passe la colonne en varchar, cohérent avec seance.statut.
-- ============================================================
SET search_path = public;

ALTER TABLE presence ALTER COLUMN statut DROP DEFAULT;
ALTER TABLE presence ALTER COLUMN statut TYPE varchar(20) USING statut::text;
ALTER TABLE presence ALTER COLUMN statut SET DEFAULT 'PRESENT';

DROP TYPE statut_presence;
