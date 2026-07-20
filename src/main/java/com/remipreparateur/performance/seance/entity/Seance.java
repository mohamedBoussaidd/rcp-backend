package com.remipreparateur.performance.seance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "seance")
@Getter
@Setter
public class Seance {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_seance_id", nullable = false)
    private TypeSeance typeSeance;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "titre")
    private String titre;

    @Column(name = "statut", nullable = false)
    private String statut = "PLANIFIEE";

    @Column(name = "heure_debut")
    private LocalTime heureDebut;

    @Column(name = "heure_fin")
    private LocalTime heureFin;

    @Column(name = "duree_minutes")
    private Short dureeMinutes;

    /** Mode avancé : temps réellement travaillé, hors mise en place / transitions (nullable). */
    @Column(name = "duree_effective_minutes")
    private Short dureeEffectiveMinutes;

    @Column(name = "terrain")
    private String terrain;

    @Column(name = "conditions_meteo")
    private String conditionsMeteo;

    @Column(name = "temperature")
    private Short temperature;

    @Column(name = "raison_ecart_duree")
    private String raisonEcartDuree;

    @Column(name = "adversaire")
    private String adversaire;

    @Column(name = "competition")
    private String competition;

    @Column(name = "domicile_exterieur")
    private String domicileExterieur;

    @Column(name = "score_match")
    private String scoreMatch;

    @Column(name = "description")
    private String description;

    /**
     * Encadrant en charge de la séance. V65 : compte staff réel au lieu d'un nom tapé à la main —
     * c'est la même liste que le staff affecté aux blocs, plus deux vocabulaires concurrents.
     */
    @Column(name = "responsable_id")
    private UUID responsableId;

    /**
     * Le problème constaté qui motive la séance (« 3 buts encaissés sur CPA samedi »).
     * <b>Staff uniquement</b> — jamais exposé dans la vue joueur de la fiche : il contient
     * souvent une critique, parfois nominative.
     */
    @Column(name = "contexte")
    private String contexte;

    /**
     * Séance ou match à l'origine de ce contexte (facultatif). Un match EST une séance dans ce
     * modèle, donc une seule colonne suffit pour pointer l'un ou l'autre.
     */
    @Column(name = "contexte_seance_id")
    private UUID contexteSeanceId;

    @Column(name = "equipe_id")
    private UUID equipeId;

    // Objectif textuel de la séance (ex-séance technique).
    @Column(name = "objectif")
    private String objectif;

    // ── Objectifs de volume fixés par le préparateur (niveau équipe, optionnels,
    //    pré-remplis depuis la somme des exercices physiques mais modifiables) ──
    @Column(name = "objectif_distance_m")
    private Integer objectifDistanceM;

    @Column(name = "objectif_intensite")
    private Short objectifIntensite;

    @Column(name = "objectif_distance_haute_intensite_m")
    private Integer objectifDistanceHauteIntensiteM;

    // ── Mode avancé (module seance_avancee) : les cinq axes pédagogiques (tous nullable) ──
    // V68 : chaque axe porte un DOSAGE 0-5 ; l'`obj*` reste la ligne de détail affichée sous
    // la jauge. Ne pas confondre avec `seance_dominante` : ce dernier tague la NATURE de la
    // séance (technique/musculaire/vivacité/PMA…), il ne dose pas ces cinq axes-là.
    @Column(name = "dominante_tactique_org_intensite")
    private Short dominanteTactiqueOrgIntensite;

    @Column(name = "obj_tactique_org")
    private String objTactiqueOrg;

    @Column(name = "dominante_tactique_fonc_intensite")
    private Short dominanteTactiqueFoncIntensite;

    @Column(name = "obj_tactique_fonc")
    private String objTactiqueFonc;

    @Column(name = "dominante_mental_intensite")
    private Short dominanteMentalIntensite;

    @Column(name = "obj_mental")
    private String objMental;

    @Column(name = "dominante_technique_intensite")
    private Short dominanteTechniqueIntensite;

    @Column(name = "obj_technique")
    private String objTechnique;

    @Column(name = "dominante_athletique_intensite")
    private Short dominanteAthletiqueIntensite;

    @Column(name = "obj_athletique")
    private String objAthletique;

    @Column(name = "cree_par")
    private UUID creePar;
}
