package com.remipreparateur.performance.seance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Créneau-jour d'un {@link ModeleSemaine} : cadre logistique + objectif/charge cible
 * par défaut, rattaché à un jour de la semaine (1 = lundi … 7 = dimanche, ISO).
 */
@Entity
@Table(name = "creneau_modele")
@Getter
@Setter
public class CreneauModele {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "modele_id", nullable = false)
    private UUID modeleId;

    /** ISO : 1 = lundi … 7 = dimanche. */
    @Column(name = "jour_semaine", nullable = false)
    private Short jourSemaine;

    @Column(name = "heure_debut")
    private LocalTime heureDebut;

    @Column(name = "duree_minutes")
    private Short dureeMinutes;

    @Column(name = "terrain")
    private String terrain;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_seance_id", nullable = false)
    private TypeSeance typeSeance;

    @Column(name = "titre")
    private String titre;

    /** Objectif / charge cible textuel par défaut. */
    @Column(name = "objectif")
    private String objectif;

    @Column(name = "objectif_distance_m")
    private Integer objectifDistanceM;

    @Column(name = "objectif_intensite")
    private Short objectifIntensite;

    @Column(name = "ordre", nullable = false)
    private Short ordre = 0;
}
