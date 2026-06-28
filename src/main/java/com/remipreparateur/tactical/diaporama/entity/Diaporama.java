package com.remipreparateur.tactical.diaporama.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Diaporama de séance : support de présentation réutilisable, niveau club, éventuellement
 * réservé à une équipe ({@code visibilite = EQUIPE}). Composé de {@link Slide} ordonnées.
 * Édition réservée au créateur ; suppression possible aussi via la permission {@code diaporama:manage}.
 */
@Entity
@Table(name = "diaporama")
@Getter
@Setter
public class Diaporama {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    /** NULL = visibilité club ; sinon équipe propriétaire (visibilité équipe). */
    @Column(name = "equipe_id")
    private UUID equipeId;

    @Column(name = "titre", nullable = false)
    private String titre;

    /** {@code CLUB} | {@code EQUIPE}. */
    @Column(name = "visibilite", nullable = false)
    private String visibilite = "CLUB";

    /** {@code BROUILLON} | {@code PUBLIE}. */
    @Column(name = "statut", nullable = false)
    private String statut = "BROUILLON";

    @Column(name = "cree_par")
    private UUID creePar;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
