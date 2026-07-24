package com.remipreparateur.badge.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Définition d'un badge (source unique de vérité). Voir {@link BadgePortee} pour la distinction
 * badge système (code-placé) / tag plateforme (assigné). Couleur : par défaut résolue via le
 * {@link BadgeTon} ; {@code couleurBg}/{@code couleurFg} sont un override explicite (obligatoire
 * pour les tags, immunisés contre les surcharges de ton des clubs).
 */
@Entity
@Table(name = "badge_definition")
@Getter
@Setter
public class BadgeDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String cle;

    @Column(nullable = false)
    private String label;

    private String icone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BadgeTon ton = BadgeTon.NEUTRAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BadgeMode mode = BadgeMode.INLINE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BadgePortee portee = BadgePortee.SYSTEME;

    @Column(name = "couleur_bg")
    private String couleurBg;

    @Column(name = "couleur_fg")
    private String couleurFg;

    private String tooltip;

    @Column(nullable = false)
    private boolean actif = true;

    @Column(nullable = false)
    private int ordre = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
