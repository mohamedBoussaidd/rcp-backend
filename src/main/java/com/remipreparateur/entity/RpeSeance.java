package com.remipreparateur.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Effort perçu (Borg CR-10, 1..10) d'un joueur sur une séance.
 * seance_id référence soit `seance` (PHYSIQUE) soit `seance_technique` (TECHNIQUE)
 * — pas de FK dure ; la durée est snapshotée pour rendre la charge auto-suffisante.
 * charge = rpe × duree_minutes (calculée par le service).
 */
@Entity
@Table(name = "rpe_seance")
@Getter
@Setter
public class RpeSeance {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "joueur_id", nullable = false)
    private UUID joueurId;

    @Column(name = "equipe_id")
    private UUID equipeId;

    @Column(name = "seance_id", nullable = false)
    private UUID seanceId;

    @Column(name = "seance_type", nullable = false)
    private String seanceType;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "rpe", nullable = false)
    private Short rpe;

    @Column(name = "duree_minutes")
    private Short dureeMinutes;

    @Column(name = "charge")
    private Integer charge;

    @Column(name = "commentaire")
    private String commentaire;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
