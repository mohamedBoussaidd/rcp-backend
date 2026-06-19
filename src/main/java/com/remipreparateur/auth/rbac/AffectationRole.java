package com.remipreparateur.auth.rbac;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Affectation d'un rôle à un utilisateur, scopée. {@code equipeId != null} → le rôle vaut pour
 * cette équipe ; {@code equipeId == null} → le rôle vaut pour TOUT le club ({@code clubId}).
 * Un utilisateur multi-rôle = plusieurs lignes ; ses permissions effectives = l'union.
 */
@Entity
@Table(name = "affectation_role")
@Getter
@Setter
public class AffectationRole {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Club de l'affectation (toujours renseigné, sert au scope club-wide). */
    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    /** null = toutes les équipes du club ; sinon une équipe précise. */
    @Column(name = "equipe_id")
    private UUID equipeId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
