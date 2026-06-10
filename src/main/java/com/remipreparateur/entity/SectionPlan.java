package com.remipreparateur.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Section d'un {@link PlanDeJeu} (phase de jeu). Éditable et réordonnable.
 * Peut porter un schéma, attaché par COPIE du {@code schemaJson} (aucune
 * synchro avec la bibliothèque de schémas une fois copié).
 */
@Entity
@Table(name = "section_plan")
@Getter
@Setter
public class SectionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "plan_de_jeu_id", nullable = false)
    private UUID planDeJeuId;

    @Column(name = "titre", nullable = false)
    private String titre;

    @Column(name = "texte", columnDefinition = "text")
    private String texte;

    @Column(name = "schema_json", columnDefinition = "text")
    private String schemaJson;

    @Column(name = "apercu", columnDefinition = "text")
    private String apercu;

    @Column(name = "ordre", nullable = false)
    private int ordre;

    @Column(name = "cree_par")
    private UUID creePar;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
