package com.remipreparateur.performance.seance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

/**
 * Exercice d'une séance-MODÈLE : référence vers la bibliothèque ({@code exerciceId}) + overrides
 * pour ce modèle (durée, intensité, attentes physiques). Miroir de {@link SeanceExercice}, recopié
 * tel quel dans la séance planifiée lors de l'instanciation.
 */
@Entity
@Table(name = "seance_modele_exercice")
@Getter
@Setter
public class SeanceModeleExercice {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "seance_modele_id", nullable = false)
    private UUID seanceModeleId;

    @Column(name = "exercice_id", nullable = false)
    private UUID exerciceId;

    @Column(name = "ordre", nullable = false)
    private Short ordre = 0;

    // ── Overrides pour ce modèle (null = valeur par défaut de l'exercice) ──
    @Column(name = "duree_minutes")
    private Short dureeMinutes;

    @Column(name = "intensite")
    private Short intensite;

    @Column(name = "distance_attendue_m")
    private Integer distanceAttendueM;

    @Column(name = "distance_haute_intensite_m")
    private Integer distanceHauteIntensiteM;

    @Column(name = "nb_sprints")
    private Short nbSprints;
}
