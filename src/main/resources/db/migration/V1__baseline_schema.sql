-- ============================================================
-- V1 — Baseline du schema existant (genere depuis pg_dump 16.4)
-- Etat de la base remi_preparateur AVANT le multi-tenant.
-- ============================================================
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
SET search_path = public;
--
-- PostgreSQL database dump
--

-- Dumped from database version 16.4
-- Dumped by pg_dump version 16.4

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--



--
-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON SCHEMA public IS 'standard public schema';


--
-- Name: update_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: agregat_joueur; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.agregat_joueur (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    joueur_id uuid NOT NULL,
    type_seance_id uuid,
    periode character varying(10),
    date_debut date NOT NULL,
    date_fin date NOT NULL,
    distance_totale_km numeric(8,3),
    distance_haute_intensite_km numeric(8,3),
    nb_sprints_total smallint,
    vitesse_max_atteinte numeric(5,2),
    nb_seances smallint,
    nb_minutes_totales smallint,
    date_calcul timestamp without time zone DEFAULT now(),
    CONSTRAINT agregat_joueur_periode_check CHECK (((periode)::text = ANY ((ARRAY['SEMAINE'::character varying, 'MOIS'::character varying, 'SAISON'::character varying])::text[])))
);


--
-- Name: baseline_joueur; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.baseline_joueur (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    joueur_id uuid NOT NULL,
    type_seance_id uuid NOT NULL,
    distance_totale_moyenne numeric(10,2),
    distance_totale_min numeric(10,2),
    distance_totale_max numeric(10,2),
    vitesse_max_moyenne numeric(5,2),
    vitesse_max_min numeric(5,2),
    vitesse_max_max numeric(5,2),
    nb_sprints_moyenne numeric(6,2),
    ratio_distance_min_moyenne numeric(8,2),
    ecart_type_distance numeric(10,2),
    nb_seances_calculees smallint,
    date_calcul timestamp without time zone DEFAULT now()
);


--
-- Name: blessure; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.blessure (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    joueur_id uuid NOT NULL,
    blessure_precedente_id uuid,
    date_blessure date NOT NULL,
    date_retour_effectif date,
    type_blessure character varying(30),
    zone_corporelle character varying(30),
    cote character varying(10),
    gravite character varying(10),
    cause_probable character varying(30),
    recidive boolean DEFAULT false,
    commentaire text,
    created_at timestamp without time zone DEFAULT now(),
    CONSTRAINT blessure_cause_probable_check CHECK (((cause_probable)::text = ANY ((ARRAY['surcharge'::character varying, 'contact'::character varying, 'terrain'::character varying, 'fatigue_accumulee'::character varying, 'recidive'::character varying, 'autre'::character varying])::text[]))),
    CONSTRAINT blessure_cote_check CHECK (((cote)::text = ANY ((ARRAY['gauche'::character varying, 'droit'::character varying, 'les_deux'::character varying])::text[]))),
    CONSTRAINT blessure_gravite_check CHECK (((gravite)::text = ANY ((ARRAY['leger'::character varying, 'modere'::character varying, 'grave'::character varying])::text[]))),
    CONSTRAINT blessure_type_blessure_check CHECK (((type_blessure)::text = ANY ((ARRAY['musculaire'::character varying, 'articulaire'::character varying, 'osseux'::character varying, 'tendineux'::character varying, 'ligamentaire'::character varying, 'autre'::character varying])::text[]))),
    CONSTRAINT blessure_zone_corporelle_check CHECK (((zone_corporelle)::text = ANY ((ARRAY['ischio_jambiers'::character varying, 'quadriceps'::character varying, 'mollet'::character varying, 'cheville'::character varying, 'genou'::character varying, 'hanche'::character varying, 'dos'::character varying, 'epaule'::character varying, 'adducteurs'::character varying, 'autre'::character varying])::text[])))
);


--
-- Name: configuration; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.configuration (
    cle character varying(100) NOT NULL,
    valeur numeric(10,4) NOT NULL,
    valeur_defaut numeric(10,4) NOT NULL,
    groupe character varying(50) NOT NULL,
    niveau integer DEFAULT 1 NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: donnee_gps; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.donnee_gps (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    joueur_id uuid NOT NULL,
    seance_id uuid NOT NULL,
    duree_minutes smallint,
    distance_totale_m numeric(10,2),
    distance_15kmh_m numeric(10,2),
    distance_19kmh_m numeric(10,2),
    distance_sprint_24kmh_m numeric(10,2),
    distance_sprint_28kmh_m numeric(10,2),
    nb_sprints_24kmh smallint,
    vitesse_max_kmh numeric(5,2),
    nb_accelerations smallint,
    nb_freinages smallint,
    ratio_distance_min numeric(8,2),
    commentaire_preparateur text,
    created_at timestamp without time zone DEFAULT now()
);


--
-- Name: historique_poids; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.historique_poids (
    id bigint NOT NULL,
    joueur_id uuid NOT NULL,
    date date NOT NULL,
    poids numeric(5,2) NOT NULL,
    commentaire character varying(255),
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: historique_poids_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.historique_poids_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: historique_poids_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.historique_poids_id_seq OWNED BY public.historique_poids.id;


--
-- Name: joueur; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.joueur (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    nom character varying(100) NOT NULL,
    prenom character varying(100) NOT NULL,
    date_naissance date,
    sexe character(1),
    poids_actuel numeric(5,2),
    poids_forme_cible numeric(5,2),
    taille numeric(5,2),
    pied_fort character varying(20),
    poste_principal character varying(30),
    poste_secondaire character varying(30),
    profil_athletique character varying(50),
    statut character varying(20) DEFAULT 'actif'::character varying,
    date_arrivee_club date,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    CONSTRAINT joueur_pied_fort_check CHECK (((pied_fort)::text = ANY ((ARRAY['gauche'::character varying, 'droit'::character varying, 'ambidextre'::character varying])::text[]))),
    CONSTRAINT joueur_poste_principal_check CHECK ((((poste_principal)::text = ANY ((ARRAY['GK'::character varying, 'DC'::character varying, 'LB'::character varying, 'RB'::character varying, 'MDC'::character varying, 'MC'::character varying, 'MG'::character varying, 'MD'::character varying, 'AG'::character varying, 'AD'::character varying, 'ATT'::character varying])::text[])) OR (poste_principal IS NULL))),
    CONSTRAINT joueur_poste_secondaire_check CHECK ((((poste_secondaire)::text = ANY ((ARRAY['GK'::character varying, 'DC'::character varying, 'LB'::character varying, 'RB'::character varying, 'MDC'::character varying, 'MC'::character varying, 'MG'::character varying, 'MD'::character varying, 'AG'::character varying, 'AD'::character varying, 'ATT'::character varying])::text[])) OR (poste_secondaire IS NULL))),
    CONSTRAINT joueur_profil_athletique_check CHECK (((profil_athletique)::text = ANY ((ARRAY['explosif_leger'::character varying, 'pivot_costaud'::character varying, 'box_to_box'::character varying, 'sentinelle'::character varying, 'lateral_offensif'::character varying, 'central_rapide'::character varying, 'central_costaud'::character varying, 'renard_surfaces'::character varying, 'attaquant_profondeur'::character varying])::text[]))),
    CONSTRAINT joueur_sexe_check CHECK ((sexe = ANY (ARRAY['M'::bpchar, 'F'::bpchar]))),
    CONSTRAINT joueur_statut_check CHECK (((statut)::text = ANY ((ARRAY['actif'::character varying, 'blesse'::character varying, 'suspendu'::character varying, 'prete'::character varying, 'inactif'::character varying])::text[])))
);


--
-- Name: recommandation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.recommandation (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    joueur_id uuid NOT NULL,
    date_recommandation timestamp without time zone DEFAULT now(),
    type character varying(30),
    score_risque smallint,
    message text,
    actions_suggerees text,
    validee_preparateur boolean,
    commentaire_preparateur text,
    created_at timestamp without time zone DEFAULT now(),
    CONSTRAINT recommandation_score_risque_check CHECK (((score_risque >= 0) AND (score_risque <= 100))),
    CONSTRAINT recommandation_type_check CHECK (((type)::text = ANY ((ARRAY['alerte_blessure'::character varying, 'surcharge'::character varying, 'baisse_performance'::character varying, 'forme_optimale'::character varying])::text[])))
);


--
-- Name: seance; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.seance (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    type_seance_id uuid NOT NULL,
    date date NOT NULL,
    heure_debut time without time zone,
    heure_fin time without time zone,
    duree_minutes smallint,
    temperature_celsius numeric(4,1),
    conditions_meteo character varying(30),
    terrain character varying(30),
    resultat_match character(1),
    score_match character varying(10),
    domicile_exterieur character varying(255),
    description text,
    created_at timestamp without time zone DEFAULT now(),
    titre character varying(255),
    statut character varying(20) DEFAULT 'PLANIFIEE'::character varying NOT NULL,
    adversaire character varying(100),
    competition character varying(100),
    raison_ecart_duree character varying(255),
    temperature smallint,
    CONSTRAINT chk_domicile_exterieur CHECK ((((domicile_exterieur)::bpchar = ANY (ARRAY['DOMICILE'::bpchar, 'EXTERIEUR'::bpchar])) OR (domicile_exterieur IS NULL))),
    CONSTRAINT chk_seance_statut CHECK (((statut)::text = ANY ((ARRAY['PLANIFIEE'::character varying, 'REALISEE'::character varying, 'ANNULEE'::character varying])::text[]))),
    CONSTRAINT seance_conditions_meteo_check CHECK (((conditions_meteo)::text = ANY ((ARRAY['beau'::character varying, 'nuageux'::character varying, 'pluie'::character varying, 'vent_fort'::character varying, 'orage'::character varying, 'neige'::character varying])::text[]))),
    CONSTRAINT seance_domicile_exterieur_check CHECK ((((domicile_exterieur)::text = ANY ((ARRAY['DOMICILE'::character varying, 'EXTERIEUR'::character varying])::text[])) OR (domicile_exterieur IS NULL))),
    CONSTRAINT seance_resultat_match_check CHECK ((resultat_match = ANY (ARRAY['V'::bpchar, 'N'::bpchar, 'D'::bpchar]))),
    CONSTRAINT seance_terrain_check CHECK ((((terrain)::text = ANY ((ARRAY['SYNTHETIQUE'::character varying, 'HERBE'::character varying, 'HERBE_GRASSE'::character varying, 'PARQUET'::character varying, 'GOUDRON'::character varying, 'FORET'::character varying])::text[])) OR (terrain IS NULL)))
);


--
-- Name: type_seance; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.type_seance (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    code character varying(20) NOT NULL,
    libelle character varying(100) NOT NULL,
    jour_semaine character varying(20),
    intensite_theorique smallint,
    objectif_principal character varying(50),
    duree_theorique_min smallint,
    created_at timestamp without time zone DEFAULT now(),
    CONSTRAINT type_seance_intensite_theorique_check CHECK (((intensite_theorique >= 1) AND (intensite_theorique <= 5)))
);


--
-- Name: vue_derniere_seance_joueur; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.vue_derniere_seance_joueur AS
 SELECT DISTINCT ON (g.joueur_id) g.joueur_id,
    s.date AS derniere_seance_date,
    ts.libelle AS type_seance,
    g.distance_totale_m,
    g.vitesse_max_kmh,
    g.ratio_distance_min
   FROM ((public.donnee_gps g
     JOIN public.seance s ON ((s.id = g.seance_id)))
     JOIN public.type_seance ts ON ((ts.id = s.type_seance_id)))
  ORDER BY g.joueur_id, s.date DESC;


--
-- Name: vue_joueur_complet; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.vue_joueur_complet AS
 SELECT id,
    nom,
    prenom,
    date_naissance,
    sexe,
    poids_actuel,
    poids_forme_cible,
    taille,
    pied_fort,
    poste_principal,
    poste_secondaire,
    profil_athletique,
    statut,
    date_arrivee_club,
    created_at,
    updated_at,
    EXTRACT(year FROM age((date_naissance)::timestamp with time zone)) AS age,
    (poids_actuel - poids_forme_cible) AS ecart_poids
   FROM public.joueur j;


--
-- Name: historique_poids id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historique_poids ALTER COLUMN id SET DEFAULT nextval('public.historique_poids_id_seq'::regclass);


--
-- Name: agregat_joueur agregat_joueur_joueur_id_type_seance_id_periode_date_debut_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agregat_joueur
    ADD CONSTRAINT agregat_joueur_joueur_id_type_seance_id_periode_date_debut_key UNIQUE (joueur_id, type_seance_id, periode, date_debut);


--
-- Name: agregat_joueur agregat_joueur_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agregat_joueur
    ADD CONSTRAINT agregat_joueur_pkey PRIMARY KEY (id);


--
-- Name: baseline_joueur baseline_joueur_joueur_id_type_seance_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.baseline_joueur
    ADD CONSTRAINT baseline_joueur_joueur_id_type_seance_id_key UNIQUE (joueur_id, type_seance_id);


--
-- Name: baseline_joueur baseline_joueur_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.baseline_joueur
    ADD CONSTRAINT baseline_joueur_pkey PRIMARY KEY (id);


--
-- Name: blessure blessure_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.blessure
    ADD CONSTRAINT blessure_pkey PRIMARY KEY (id);


--
-- Name: configuration configuration_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.configuration
    ADD CONSTRAINT configuration_pkey PRIMARY KEY (cle);


--
-- Name: donnee_gps donnee_gps_joueur_id_seance_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.donnee_gps
    ADD CONSTRAINT donnee_gps_joueur_id_seance_id_key UNIQUE (joueur_id, seance_id);


--
-- Name: donnee_gps donnee_gps_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.donnee_gps
    ADD CONSTRAINT donnee_gps_pkey PRIMARY KEY (id);


--
-- Name: historique_poids historique_poids_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historique_poids
    ADD CONSTRAINT historique_poids_pkey PRIMARY KEY (id);


--
-- Name: joueur joueur_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.joueur
    ADD CONSTRAINT joueur_pkey PRIMARY KEY (id);


--
-- Name: recommandation recommandation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recommandation
    ADD CONSTRAINT recommandation_pkey PRIMARY KEY (id);


--
-- Name: seance seance_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seance
    ADD CONSTRAINT seance_pkey PRIMARY KEY (id);


--
-- Name: type_seance type_seance_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.type_seance
    ADD CONSTRAINT type_seance_code_key UNIQUE (code);


--
-- Name: type_seance type_seance_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.type_seance
    ADD CONSTRAINT type_seance_pkey PRIMARY KEY (id);


--
-- Name: historique_poids uq_historique_poids_joueur_date; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historique_poids
    ADD CONSTRAINT uq_historique_poids_joueur_date UNIQUE (joueur_id, date);


--
-- Name: idx_agregat_joueur; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_agregat_joueur ON public.agregat_joueur USING btree (joueur_id);


--
-- Name: idx_agregat_periode; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_agregat_periode ON public.agregat_joueur USING btree (periode, date_debut, date_fin);


--
-- Name: idx_baseline_joueur; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_baseline_joueur ON public.baseline_joueur USING btree (joueur_id);


--
-- Name: idx_baseline_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_baseline_type ON public.baseline_joueur USING btree (type_seance_id);


--
-- Name: idx_blessure_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_blessure_date ON public.blessure USING btree (date_blessure);


--
-- Name: idx_blessure_joueur; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_blessure_joueur ON public.blessure USING btree (joueur_id);


--
-- Name: idx_blessure_zone; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_blessure_zone ON public.blessure USING btree (zone_corporelle);


--
-- Name: idx_gps_joueur; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_gps_joueur ON public.donnee_gps USING btree (joueur_id);


--
-- Name: idx_gps_joueur_seance; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_gps_joueur_seance ON public.donnee_gps USING btree (joueur_id, seance_id);


--
-- Name: idx_gps_seance; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_gps_seance ON public.donnee_gps USING btree (seance_id);


--
-- Name: idx_historique_poids_joueur_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_historique_poids_joueur_date ON public.historique_poids USING btree (joueur_id, date DESC);


--
-- Name: idx_joueur_poste; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_joueur_poste ON public.joueur USING btree (poste_principal);


--
-- Name: idx_joueur_statut; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_joueur_statut ON public.joueur USING btree (statut);


--
-- Name: idx_reco_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reco_date ON public.recommandation USING btree (date_recommandation);


--
-- Name: idx_reco_joueur; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reco_joueur ON public.recommandation USING btree (joueur_id);


--
-- Name: idx_reco_score; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reco_score ON public.recommandation USING btree (score_risque);


--
-- Name: idx_seance_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_seance_date ON public.seance USING btree (date);


--
-- Name: idx_seance_date_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_seance_date_type ON public.seance USING btree (date, type_seance_id);


--
-- Name: idx_seance_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_seance_type ON public.seance USING btree (type_seance_id);


--
-- Name: joueur trg_joueur_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_joueur_updated_at BEFORE UPDATE ON public.joueur FOR EACH ROW EXECUTE FUNCTION public.update_updated_at();


--
-- Name: agregat_joueur agregat_joueur_joueur_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agregat_joueur
    ADD CONSTRAINT agregat_joueur_joueur_id_fkey FOREIGN KEY (joueur_id) REFERENCES public.joueur(id);


--
-- Name: agregat_joueur agregat_joueur_type_seance_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.agregat_joueur
    ADD CONSTRAINT agregat_joueur_type_seance_id_fkey FOREIGN KEY (type_seance_id) REFERENCES public.type_seance(id);


--
-- Name: baseline_joueur baseline_joueur_joueur_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.baseline_joueur
    ADD CONSTRAINT baseline_joueur_joueur_id_fkey FOREIGN KEY (joueur_id) REFERENCES public.joueur(id);


--
-- Name: baseline_joueur baseline_joueur_type_seance_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.baseline_joueur
    ADD CONSTRAINT baseline_joueur_type_seance_id_fkey FOREIGN KEY (type_seance_id) REFERENCES public.type_seance(id);


--
-- Name: blessure blessure_blessure_precedente_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.blessure
    ADD CONSTRAINT blessure_blessure_precedente_id_fkey FOREIGN KEY (blessure_precedente_id) REFERENCES public.blessure(id);


--
-- Name: blessure blessure_joueur_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.blessure
    ADD CONSTRAINT blessure_joueur_id_fkey FOREIGN KEY (joueur_id) REFERENCES public.joueur(id);


--
-- Name: donnee_gps donnee_gps_joueur_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.donnee_gps
    ADD CONSTRAINT donnee_gps_joueur_id_fkey FOREIGN KEY (joueur_id) REFERENCES public.joueur(id);


--
-- Name: donnee_gps donnee_gps_seance_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.donnee_gps
    ADD CONSTRAINT donnee_gps_seance_id_fkey FOREIGN KEY (seance_id) REFERENCES public.seance(id);


--
-- Name: historique_poids historique_poids_joueur_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.historique_poids
    ADD CONSTRAINT historique_poids_joueur_id_fkey FOREIGN KEY (joueur_id) REFERENCES public.joueur(id) ON DELETE CASCADE;


--
-- Name: recommandation recommandation_joueur_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recommandation
    ADD CONSTRAINT recommandation_joueur_id_fkey FOREIGN KEY (joueur_id) REFERENCES public.joueur(id);


--
-- Name: seance seance_type_seance_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seance
    ADD CONSTRAINT seance_type_seance_id_fkey FOREIGN KEY (type_seance_id) REFERENCES public.type_seance(id);


--
-- PostgreSQL database dump complete
--
