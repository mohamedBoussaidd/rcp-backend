package com.remipreparateur.performance.seance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

/**
 * Exercice d'une séance : référence vers la bibliothèque ({@code exerciceId}) + valeurs
 * surchargeables pour CETTE séance (durée, intensité, attentes physiques). Les overrides
 * null = on retombe sur les valeurs par défaut de l'exercice.
 */
@Entity
@Table(name = "seance_exercice")
@Getter
@Setter
public class SeanceExercice {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "seance_id", nullable = false)
    private UUID seanceId;

    @Column(name = "exercice_id", nullable = false)
    private UUID exerciceId;

    @Column(name = "ordre", nullable = false)
    private Short ordre = 0;

    /** Bloc de rattachement en mode avancé (NULL = liste plate historique). */
    @Column(name = "bloc_id")
    private UUID blocId;

    // ── Overrides pour cette séance (null = valeur par défaut de l'exercice) ──
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
