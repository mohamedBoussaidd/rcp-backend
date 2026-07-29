-- ============================================================
-- V89 — Catalogue des FOURNISSEURS IA (super-admin), pour ajouter un fournisseur sans redéployer
--
-- Jusqu'ici la clé globale ne pouvait venir que d'une variable d'environnement du serveur
-- (ANTHROPIC_API_KEY / OPENAI_API_KEY) : invisible depuis l'application, impossible à changer
-- sans toucher au VPS, et silencieusement absente quand le processus n'héritait pas de la variable.
--
-- Cette table rend le catalogue DONNÉE : code, clé chiffrée, modèle par défaut et surtout
-- `dialecte` + `base_url`. Le dialecte dit COMMENT parler au fournisseur :
--   · OPENAI    → POST {base_url}/chat/completions, en-tête `Authorization: Bearer`
--                 (Mistral, Groq, DeepSeek, Together, OpenRouter, xAI, Ollama… parlent ce dialecte) ;
--   · ANTHROPIC → SDK Anthropic officiel.
-- Ajouter un fournisseur compatible avec l'un de ces deux dialectes = une ligne ici, zéro code.
-- Un fournisseur au protocole propriétaire (Gemini natif, Bedrock, Vertex) demanderait, lui, un
-- nouveau client Java : c'est la limite assumée du data-driven.
--
-- `cle_chiffree` : AES-GCM via CryptoService (secret IA_KEYS_SECRET), comme les clés par club.
-- NULL = pas de clé en base → repli sur la variable d'environnement du même nom (rétrocompat :
-- les deux fournisseurs seedés ici continuent de marcher exactement comme avant cette migration).
-- ============================================================
SET search_path = public;

CREATE TABLE IF NOT EXISTS ia_fournisseur (
    code          varchar(40)  NOT NULL,
    libelle       varchar(80)  NOT NULL,
    dialecte      varchar(20)  NOT NULL,
    base_url      varchar(200),
    cle_chiffree  text,
    modele_defaut varchar(80),
    actif         boolean      NOT NULL DEFAULT true,
    maj_le        timestamp    NOT NULL DEFAULT now(),
    CONSTRAINT ia_fournisseur_pkey PRIMARY KEY (code),
    CONSTRAINT ia_fournisseur_dialecte_check CHECK (dialecte IN ('OPENAI', 'ANTHROPIC'))
);

INSERT INTO ia_fournisseur (code, libelle, dialecte, base_url, modele_defaut, actif) VALUES
    ('ANTHROPIC', 'Anthropic (Claude)', 'ANTHROPIC', NULL,                         'claude-opus-4-8', true),
    ('OPENAI',    'OpenAI',             'OPENAI',    'https://api.openai.com/v1',  'gpt-4o',          true)
ON CONFLICT (code) DO NOTHING;
