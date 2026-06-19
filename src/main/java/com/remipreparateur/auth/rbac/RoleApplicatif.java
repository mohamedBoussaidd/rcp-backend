package com.remipreparateur.auth.rbac;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Rôle applicatif = paquet nommé de permissions. {@code clubId == null} → rôle SYSTÈME (commun à
 * tous les clubs, seedé, non supprimable). {@code clubId != null} → rôle CUSTOM propre à un club.
 *
 * <p>Les permissions associées vivent dans la table {@code role_permission} (cf. {@link RolePermission}).
 */
@Entity
@Table(name = "app_role")
@Getter
@Setter
public class RoleApplicatif {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /** null = rôle système commun à tous les clubs ; sinon rôle custom propre à ce club. */
    @Column(name = "club_id")
    private UUID clubId;

    /** Identifiant stable lisible (ex. PREPARATEUR). Unique par club (et parmi les rôles système). */
    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "libelle", nullable = false, length = 80)
    private String libelle;

    @Column(name = "systeme", nullable = false)
    private boolean systeme = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
