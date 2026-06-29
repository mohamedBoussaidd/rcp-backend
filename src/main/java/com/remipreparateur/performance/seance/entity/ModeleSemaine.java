package com.remipreparateur.performance.seance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Gabarit hebdomadaire d'une équipe (ex. « Semaine championnat », « Semaine de prépa »).
 * Sert à instancier des séances réelles sur une plage de dates, sans synchro ultérieure.
 */
@Entity
@Table(name = "modele_semaine")
@Getter
@Setter
public class ModeleSemaine {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "equipe_id", nullable = false)
    private UUID equipeId;

    @Column(name = "nom", nullable = false)
    private String nom;

    @Column(name = "description")
    private String description;

    @Column(name = "cree_par")
    private UUID creePar;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
