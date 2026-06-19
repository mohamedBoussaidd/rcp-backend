package com.remipreparateur.auth.rbac;

import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.club.entity.Equipe;
import com.remipreparateur.club.repository.EquipeRepository;
import com.remipreparateur.shared.security.ContexteActif;
import com.remipreparateur.shared.security.ContexteActifHolder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Calcule l'ensemble des permissions effectives d'un utilisateur POUR LE CONTEXTE ACTIF
 * (club + équipe(s)). Résolution côté serveur à chaque requête : un changement de droit ou
 * d'abonnement prend effet immédiatement, et le scope par équipe interdit de figer ça dans le JWT.
 *
 * <p>Permissions = union des permissions des rôles affectés à l'utilisateur qui « couvrent » le
 * contexte (affectation sur l'équipe active, ou affectation club-wide sur le club actif).
 *
 * <p>Cas particuliers hors RBAC : SUPER_ADMIN = toutes les permissions (bypass) ; JOUEUR = aucune
 * (son accès passe par le self-scope /api/moi/**).
 */
@Service
public class PermissionResolver {

    private final AffectationRoleRepository affectations;
    private final RolePermissionRepository rolePermissions;
    private final EquipeRepository equipeRepository;

    public PermissionResolver(AffectationRoleRepository affectations,
                              RolePermissionRepository rolePermissions,
                              EquipeRepository equipeRepository) {
        this.affectations = affectations;
        this.rolePermissions = rolePermissions;
        this.equipeRepository = equipeRepository;
    }

    /** Codes de permission (ex. {@code seances:write}) effectifs de l'utilisateur dans le contexte actif. */
    public Set<String> permissionsPour(Utilisateur u) {
        if (u.getRole() == Role.SUPER_ADMIN) {
            return Arrays.stream(Permission.values()).map(Permission::getCode).collect(Collectors.toSet());
        }

        ContexteActif ctx = ContexteActifHolder.get();
        UUID clubActif = clubActif(u, ctx);
        Set<UUID> equipesActives = equipesActives(u, ctx, clubActif);

        List<AffectationRole> affs = affectations.findByUserId(u.getId());
        Set<UUID> roleIds = affs.stream()
                .filter(a -> couvre(a, clubActif, equipesActives))
                .map(AffectationRole::getRoleId)
                .collect(Collectors.toSet());

        if (roleIds.isEmpty()) {
            return Set.of();
        }
        return rolePermissions.findByRoleIdIn(roleIds).stream()
                .map(RolePermission::getPermission)
                .collect(Collectors.toSet());
    }

    /** Une affectation couvre-t-elle le contexte actif ? (équipe précise, ou club entier) */
    private boolean couvre(AffectationRole a, UUID clubActif, Set<UUID> equipesActives) {
        if (a.getEquipeId() == null) {
            // affectation club-wide : couvre toute équipe du club de l'affectation
            return clubActif != null && clubActif.equals(a.getClubId());
        }
        return equipesActives.contains(a.getEquipeId());
    }

    /** Club actif : contexte de navigation, sinon club de l'utilisateur, sinon club de son équipe. */
    private UUID clubActif(Utilisateur u, ContexteActif ctx) {
        if (ctx != null && ctx.clubId() != null) {
            return ctx.clubId();
        }
        if (u.getClubId() != null) {
            return u.getClubId();
        }
        if (u.getEquipeId() != null) {
            return equipeRepository.findById(u.getEquipeId()).map(Equipe::getClubId).orElse(null);
        }
        return null;
    }

    /** Équipes en jeu : contexte explicite, sinon l'équipe du staff, sinon toutes celles du club. */
    private Set<UUID> equipesActives(Utilisateur u, ContexteActif ctx, UUID clubActif) {
        if (ctx != null && !ctx.equipeIds().isEmpty()) {
            return new HashSet<>(ctx.equipeIds());
        }
        if (u.getEquipeId() != null) {
            return Set.of(u.getEquipeId());
        }
        if (clubActif != null) {
            return equipeRepository.findByClubId(clubActif).stream()
                    .map(Equipe::getId).collect(Collectors.toSet());
        }
        return Set.of();
    }
}
