package com.remipreparateur.performance.gps.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.performance.seance.entity.Seance;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "donnee_gps")
@Getter
@Setter
public class DonneeGps {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "joueur_id", nullable = false)
    private Joueur joueur;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seance_id", nullable = false)
    private Seance seance;

    @Column(name = "duree_minutes")
    private Short dureeMinutes;

    @Column(name = "distance_totale_m")
    private BigDecimal distanceTotaleM;

    @Column(name = "distance_15kmh_m")
    private BigDecimal distance15kmhM;

    @Column(name = "distance_19kmh_m")
    private BigDecimal distance19kmhM;

    @Column(name = "distance_sprint_24kmh_m")
    private BigDecimal distanceSprint24kmhM;

    @Column(name = "distance_sprint_28kmh_m")
    private BigDecimal distanceSprint28kmhM;

    @Column(name = "nb_sprints_24kmh")
    private Short nbSprints24kmh;

    @Column(name = "vitesse_max_kmh")
    private BigDecimal vitesseMaxKmh;

    @Column(name = "nb_accelerations")
    private Short nbAccelerations;

    @Column(name = "nb_freinages")
    private Short nbFreinages;

    @Column(name = "ratio_distance_min")
    private BigDecimal ratioDistanceMin;

    @Column(name = "commentaire_preparateur")
    private String commentairePreparateur;
}
