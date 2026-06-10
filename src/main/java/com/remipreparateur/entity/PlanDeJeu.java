package com.remipreparateur.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Plan de jeu (« document d'identité équipe »), unique par équipe.
 * Document vivant composé de {@link SectionPlan} ordonnées. Créé à la volée
 * au premier accès, avec 6 sections standard pré-remplies (cf. service).
 */
@Entity
@Table(name = "plan_de_jeu")
@Getter
@Setter
public class PlanDeJeu {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "equipe_id", nullable = false)
    private UUID equipeId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
