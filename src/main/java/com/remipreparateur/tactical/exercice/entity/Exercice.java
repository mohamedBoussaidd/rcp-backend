package com.remipreparateur.tactical.exercice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "exercice")
@Getter
@Setter
public class Exercice {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(name = "nom", nullable = false)
    private String nom;

    @Column(name = "categorie")
    private String categorie;

    // PHYSIQUE / TECHNIQUE / MIXTE : oriente le contenu et l'usage des attentes physiques.
    @Column(name = "type", nullable = false, length = 20)
    private String type = "TECHNIQUE";

    @Column(name = "duree_minutes")
    private Short dureeMinutes;

    @Column(name = "objectif")
    private String objectif;

    @Column(name = "intensite")
    private Short intensite;

    // ── Attentes physiques (optionnelles, surtout pour les exercices PHYSIQUE) ──
    @Column(name = "distance_attendue_m")
    private Integer distanceAttendueM;

    @Column(name = "distance_haute_intensite_m")
    private Integer distanceHauteIntensiteM;

    @Column(name = "nb_sprints")
    private Short nbSprints;

    @Column(name = "description")
    private String description;

    // ── Mode avancé (module seance_avancee) : cadre pédagogique — tout optionnel ──
    @Column(name = "contexte_pedagogique")
    private String contextePedagogique;

    // TEMPS_DE_JEU / PRINCIPE_ACTION / REGLE_ACTION_COLLECTIVE / REGLE_ACTION_INDIVIDUELLE / MOYEN
    @Column(name = "niveau_objectif", length = 40)
    private String niveauObjectif;

    // COLLECTIF / INTERSECTORIEL / SECTORIEL / GROUPAL / INDIVIDUEL
    @Column(name = "echelle_effectif", length = 20)
    private String echelleEffectif;

    @Column(name = "dominante_tactique_org")
    private String dominanteTactiqueOrg;

    @Column(name = "dominante_tactique_fonc")
    private String dominanteTactiqueFonc;

    @Column(name = "dominante_mental")
    private String dominanteMental;

    @Column(name = "dominante_technique")
    private String dominanteTechnique;

    @Column(name = "dominante_athletique")
    private String dominanteAthletique;

    @Column(name = "but_systeme_marque")
    private String butSystemeMarque;

    @Column(name = "regles_jeu")
    private String reglesJeu;

    @Column(name = "variables_pedagogiques")
    private String variablesPedagogiques;

    @Column(name = "reperes_perceptifs")
    private String reperesPerceptifs;

    @Column(name = "comportements_attendus")
    private String comportementsAttendus;

    // ── Mode avancé : organisation (densité m²/joueur calculée côté front, jamais stockée) ──
    @Column(name = "terrain_longueur_m", precision = 5, scale = 1)
    private java.math.BigDecimal terrainLongueurM;

    @Column(name = "terrain_largeur_m", precision = 5, scale = 1)
    private java.math.BigDecimal terrainLargeurM;

    @Column(name = "format_joueurs", length = 120)
    private String formatJoueurs;

    @Column(name = "nb_joueurs_total")
    private Short nbJoueursTotal;

    @Column(name = "sequencage", length = 120)
    private String sequencage;

    @Column(name = "schema_json", columnDefinition = "text")
    private String schemaJson;

    /** Import photo d'origine (pièce jointe consultable), nullable. */
    @Column(name = "photo_import_id")
    private UUID photoImportId;

    @Column(name = "cree_par")
    private UUID creePar;

    @Column(name = "equipe_origine_id")
    private UUID equipeOrigineId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
