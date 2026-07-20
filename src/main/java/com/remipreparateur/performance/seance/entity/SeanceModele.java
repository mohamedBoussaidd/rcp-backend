package com.remipreparateur.performance.seance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Séance-MODÈLE : gabarit de séance RÉUTILISABLE (bibliothèque de l'espace Coaching), à ne pas
 * confondre avec {@link Seance} (l'instance PLANIFIÉE dans le calendrier). Un modèle porte le
 * contenu — type, objectif, durée, objectifs de volume, liste d'exercices ordonnés — mais AUCUN
 * ancrage temporel (ni date, ni statut, ni météo). On l'« instancie » via {@code planifier} pour
 * créer une vraie {@link Seance} à une date + équipe choisies.
 *
 * <p>Scope CLUB et règle « créateur-only » à l'édition, identiques à la bibliothèque d'exercices.
 */
@Entity
@Table(name = "seance_modele")
@Getter
@Setter
public class SeanceModele {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(name = "nom", nullable = false)
    private String nom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_seance_id", nullable = false)
    private TypeSeance typeSeance;

    @Column(name = "objectif")
    private String objectif;

    @Column(name = "duree_minutes")
    private Short dureeMinutes;

    // ── Objectifs de volume (niveau équipe, optionnels ; recopiés dans la séance planifiée) ──
    @Column(name = "objectif_distance_m")
    private Integer objectifDistanceM;

    @Column(name = "objectif_intensite")
    private Short objectifIntensite;

    @Column(name = "objectif_distance_haute_intensite_m")
    private Integer objectifDistanceHauteIntensiteM;

    @Column(name = "description")
    private String description;

    @Column(name = "cree_par")
    private UUID creePar;

    @Column(name = "equipe_origine_id")
    private UUID equipeOrigineId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // ── Mode avancé (V63) : objectifs pédagogiques, recopiés à la planification ──

    @Column(name = "obj_tactique_org")
    private String objTactiqueOrg;

    @Column(name = "obj_tactique_fonc")
    private String objTactiqueFonc;

    @Column(name = "obj_mental")
    private String objMental;

    @Column(name = "obj_technique")
    private String objTechnique;

    @Column(name = "obj_athletique")
    private String objAthletique;
}
