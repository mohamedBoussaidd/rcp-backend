package com.remipreparateur.auth.rbac;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** DTOs de l'administration des rôles & accès (gérée par le président dans son club). */
public final class RbacDtos {

    private RbacDtos() {}

    /** Une permission du catalogue, pour la matrice. */
    public record PermissionDto(String code, String module, String libelle) {}

    /** Un rôle (système ou custom du club) avec ses permissions et son usage. */
    public record RoleDto(
            UUID id,
            String code,
            String libelle,
            boolean systeme,
            List<String> permissions,
            long nbAffectations) {}

    /** Création / mise à jour d'un rôle custom. */
    public record RoleUpsertRequest(
            @NotBlank String libelle,
            @NotNull List<String> permissions) {}

    /** Une affectation d'un membre (rôle sur une équipe). */
    public record AffectationDto(
            UUID id,
            UUID roleId,
            String roleLibelle,
            boolean systeme,
            UUID equipeId,
            String equipeNom) {}

    /** Un rôle à attribuer (sur une équipe ; equipeId null = tout le club). */
    public record AffectationItem(
            @NotNull UUID roleId,
            UUID equipeId) {}

    /** Remplace l'ensemble des rôles d'un membre. */
    public record DefinirRolesRequest(
            @NotNull List<AffectationItem> roles) {}
}
