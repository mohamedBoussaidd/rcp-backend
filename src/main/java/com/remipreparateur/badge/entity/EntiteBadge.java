package com.remipreparateur.badge.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Assignation d'un tag (badge de portée PLATEFORME) à une entité (exercice / séance / joueur).
 * Table polymorphe (type + id) : l'affichage des tags d'une entité vient d'une simple recherche
 * par (type, id).
 */
@Entity
@Table(name = "entite_badge")
@Getter
@Setter
public class EntiteBadge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_entite", nullable = false)
    private TypeEntite typeEntite;

    @Column(name = "entite_id", nullable = false)
    private UUID entiteId;

    @Column(name = "badge_id", nullable = false)
    private UUID badgeId;

    @Column(name = "cree_at")
    private LocalDateTime creeAt;

    @PrePersist
    void onCreate() {
        if (creeAt == null) creeAt = LocalDateTime.now();
    }
}
