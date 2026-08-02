package com.remipreparateur.joueur.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Objectif individuel fixé à un joueur par le staff sportif (V98).
 * Quelques objectifs datés, revus en entretien — pas un plan d'entraînement.
 */
@Entity
@Table(name = "objectif_joueur")
@Getter
@Setter
public class ObjectifJoueur {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "joueur_id", nullable = false)
    private UUID joueurId;

    @Column(name = "titre", nullable = false)
    private String titre;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "echeance")
    private LocalDate echeance;

    /** EN_COURS | ATTEINT | ABANDONNE (contrainte CHECK en base). */
    @Column(name = "statut", nullable = false)
    private String statut = "EN_COURS";

    @Column(name = "cree_par")
    private UUID creePar;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
