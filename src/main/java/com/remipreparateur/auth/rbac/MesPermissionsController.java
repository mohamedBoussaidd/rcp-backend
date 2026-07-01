package com.remipreparateur.auth.rbac;

import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.club.pack.ClubModulesService;
import com.remipreparateur.shared.security.CurrentUserProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Permissions effectives de l'utilisateur courant POUR LE CONTEXTE ACTIF (en-têtes X-Contexte-*).
 * Le front s'en sert pour piloter l'affichage (masquage UI) ; la sécurité reste côté backend.
 */
@RestController
@RequestMapping("/api/me")
public class MesPermissionsController {

    private final CurrentUserProvider currentUser;
    private final PermissionResolver permissionResolver;
    private final ClubModulesService clubModulesService;

    public MesPermissionsController(CurrentUserProvider currentUser, PermissionResolver permissionResolver,
                                    ClubModulesService clubModulesService) {
        this.currentUser = currentUser;
        this.permissionResolver = permissionResolver;
        this.clubModulesService = clubModulesService;
    }

    @GetMapping("/permissions")
    public List<String> mesPermissions() {
        return permissionResolver.permissionsPour(currentUser.current()).stream().sorted().toList();
    }

    /**
     * Modules ACTIFS du club de l'utilisateur pour le contexte actif. Le front s'en sert pour masquer
     * les entrées de menu / écrans des modules non souscrits (la sécurité reste côté backend).
     * SUPER_ADMIN : tous les modules (bypass), pour pouvoir tout administrer.
     */
    @GetMapping("/modules")
    public List<String> mesModules() {
        Utilisateur u = currentUser.current();
        if (u.getRole() == Role.SUPER_ADMIN) {
            return FeatureModule.tousCodes();
        }
        UUID clubId = permissionResolver.clubActif(u);
        return clubModulesService.modulesActifs(clubId).stream().sorted().toList();
    }
}
