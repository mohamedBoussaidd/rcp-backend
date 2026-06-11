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

    @Column(name = "equipe_id")
    private UUID equipeId;
}
