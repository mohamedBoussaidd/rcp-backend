package com.remipreparateur.badge.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Couleur par défaut (paire {fond, texte}) d'un {@link BadgeTon}, éditable par le super-admin.
 * Sert de palette canonique : les badges système et les tags à couleur non explicite en héritent.
 * Le front garde ces mêmes défauts en {@code :root} (theme-aware) ; cette table porte les valeurs
 * éditées.
 */
@Entity
@Table(name = "badge_palette_ton")
@Getter
@Setter
public class BadgePaletteTon {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "ton")
    private BadgeTon ton;

    @Column(nullable = false)
    private String libelle;

    @Column(name = "couleur_bg", nullable = false)
    private String couleurBg;

    @Column(name = "couleur_fg", nullable = false)
    private String couleurFg;

    /** Le super-admin a-t-il édité ce ton ? Sinon le front garde le défaut theme-aware de :root. */
    @Column(nullable = false)
    private boolean personnalise = false;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
