package com.remipreparateur.shared.security;

import com.remipreparateur.auth.rbac.PermissionResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Enrichit l'authentification courante avec les permissions effectives (autorités au format
 * {@code module:action}) résolues pour le contexte actif. Placé APRÈS {@link ContexteFilter}
 * (le contexte équipe doit être posé) et AVANT l'autorisation des requêtes.
 *
 * <p>Les autorités {@code ROLE_*} d'origine sont CONSERVÉES (compat : {@code hasRole('JOUEUR')}
 * pour /api/moi, et les {@code @PreAuthorize} de gestion club restent valides pendant le dual-run).
 */
@Component
public class PermissionAuthoritiesFilter extends OncePerRequestFilter {

    private final PermissionResolver permissionResolver;

    public PermissionAuthoritiesFilter(PermissionResolver permissionResolver) {
        this.permissionResolver = permissionResolver;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails details) {
            List<GrantedAuthority> authorities = new ArrayList<>(auth.getAuthorities()); // garde les ROLE_*
            permissionResolver.permissionsPour(details.getUtilisateur())
                    .forEach(perm -> authorities.add(new SimpleGrantedAuthority(perm)));

            var enrichi = new UsernamePasswordAuthenticationToken(details, null, authorities);
            enrichi.setDetails(auth.getDetails());
            SecurityContextHolder.getContext().setAuthentication(enrichi);
        }

        filterChain.doFilter(request, response);
    }
}
