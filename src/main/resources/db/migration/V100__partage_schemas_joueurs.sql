-- ============================================================
-- V100 — Partage de schémas tactiques aux joueurs (PWA)
--
-- Le staff partage un SCHÉMA (jamais un diaporama ni une diapo : décision produit) à une
-- ÉQUIPE entière et/ou à des JOUEURS choisis. Le joueur le retrouve dans son espace et le
-- rejoue avec le même lecteur que le staff (`SchemaViewerComponent`).
--
-- Une ligne = un partage vers UNE cible. Équipe et joueur sont exclusifs dans les faits mais
-- la contrainte n'exige que « au moins une des deux » : un même schéma envoyé à l'équipe puis
-- à un joueur en particulier produit deux lignes, ce qui garde l'historique lisible.
--
-- Le contenu n'est PAS recopié : on référence `schema_tactique`. Corriger un schéma corrige
-- donc ce que voient les joueurs — l'inverse du diaporama, qui est un instantané figé, et
-- c'est voulu : un schéma partagé est une consigne vivante.
--
-- Nouveau module activable `schemas_joueur` (add-on, cf. FeatureModule.SCHEMAS_JOUEUR) :
-- inclus dans les packs Performance et Complet, activable partout via la surcharge
-- `club_module` du super-admin. Double verrou : module actif ET permission `schemas:partager`.
-- ============================================================
SET search_path = public;

CREATE TABLE IF NOT EXISTS schema_partage (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id     uuid NOT NULL REFERENCES club (id)            ON DELETE CASCADE,
    schema_id   uuid NOT NULL REFERENCES schema_tactique (id) ON DELETE CASCADE,
    equipe_id   uuid          REFERENCES equipe (id)          ON DELETE CASCADE,
    joueur_id   uuid          REFERENCES joueur (id)          ON DELETE CASCADE,
    titre       varchar(160),
    message     text,
    cree_par    uuid,
    created_at  timestamp NOT NULL DEFAULT now(),
    CONSTRAINT schema_partage_cible_chk CHECK (equipe_id IS NOT NULL OR joueur_id IS NOT NULL)
);

-- Lectures réelles : « ce que voit CE joueur » (équipe ou nominatif) et « qui a reçu CE schéma ».
CREATE INDEX IF NOT EXISTS idx_schema_partage_equipe ON schema_partage (equipe_id);
CREATE INDEX IF NOT EXISTS idx_schema_partage_joueur ON schema_partage (joueur_id);
CREATE INDEX IF NOT EXISTS idx_schema_partage_schema ON schema_partage (schema_id);

COMMENT ON TABLE schema_partage IS
    'Diffusion d''un schéma tactique aux joueurs (équipe entière et/ou joueurs nommés).';

-- ── Module produit ──
INSERT INTO pack_module (pack_code, module_code) VALUES
 ('performance', 'schemas_joueur'),
 ('complet',     'schemas_joueur')
ON CONFLICT DO NOTHING;

-- ── Permission ──
-- Partager est un geste d'ENTRAÎNEUR (il engage la parole du staff auprès du groupe), distinct
-- de `schemas:write` qui n'est qu'un droit d'édition. SUPER_ADMIN a tout d'office (hors RBAC).
INSERT INTO role_permission (role_id, permission)
SELECT r.id, 'schemas:partager'
FROM (VALUES
   ('a0000000-0000-0000-0000-000000000002'::uuid),   -- ENTRAINEUR
   ('a0000000-0000-0000-0000-000000000006'::uuid),   -- ENTRAINEUR_CHEF
   ('a0000000-0000-0000-0000-000000000003'::uuid)    -- PREPARATEUR
) AS r(id)
ON CONFLICT DO NOTHING;
