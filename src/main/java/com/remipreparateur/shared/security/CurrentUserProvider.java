package com.remipreparateur.shared.security;

import com.remipreparateur.auth.entity.Utilisateur;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Recupere l'utilisateur authentifie depuis le SecurityContext. */
@Component
public class CurrentUserProvider {

    public Utilisateur current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails details) {
            return details.getUtilisateur();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Non authentifie");
    }
}
