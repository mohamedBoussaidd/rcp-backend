package com.remipreparateur.entretien.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Auto-évaluation d'un joueur sur un de SES axes de travail, indépendante d'un entretien.
 * Note 1..5 + commentaire optionnel. Plafonnée à une par axe et par semaine (contrôle service).
 */
@Entity
@Table(name = "auto_evaluation")
@Getter
@Setter
public class AutoEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "joueur_id", nullable = false)
    private UUID joueurId;

    @Column(name = "axe_travail_id", nullable = false)
    private UUID axeTravailId;

    @Column(name = "note", nullable = false)
    private Integer note;

    @Column(name = "commentaire")
    private String commentaire;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
