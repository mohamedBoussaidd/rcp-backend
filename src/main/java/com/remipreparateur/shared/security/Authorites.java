package com.remipreparateur.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Vérification ponctuelle d'une permission effective (RBAC ∩ modules actifs, posée par
 * {@link PermissionAuthoritiesFilter}) depuis un service — pour du gating de CHAMPS,
 * là où les règles d'URL de SecurityConfig ne suffisent pas.
 */
public final class Authorites {

    private Authorites() {}

    public static boolean possede(String code) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(g -> code.equals(g.getAuthority()));
    }
}
