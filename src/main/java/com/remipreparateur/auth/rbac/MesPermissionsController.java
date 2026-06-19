package com.remipreparateur.auth.rbac;

import com.remipreparateur.shared.security.CurrentUserProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Permissions effectives de l'utilisateur courant POUR LE CONTEXTE ACTIF (en-têtes X-Contexte-*).
 * Le front s'en sert pour piloter l'affichage (masquage UI) ; la sécurité reste côté backend.
 */
@RestController
@RequestMapping("/api/me")
public class MesPermissionsController {

    private final CurrentUserProvider currentUser;
    private final PermissionResolver permissionResolver;

    public MesPermissionsController(CurrentUserProvider currentUser, PermissionResolver permissionResolver) {
        this.currentUser = currentUser;
        this.permissionResolver = permissionResolver;
    }

    @GetMapping("/permissions")
    public List<String> mesPermissions() {
        return permissionResolver.permissionsPour(currentUser.current()).stream().sorted().toList();
    }
}
