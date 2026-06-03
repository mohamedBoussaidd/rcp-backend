package com.remipreparateur.security;

import com.remipreparateur.entity.Equipe;
import com.remipreparateur.entity.Utilisateur;
import com.remipreparateur.repository.EquipeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Determine la portee (Scope) des donnees visibles pour l'utilisateur courant,
 * et l'equipe a affecter lors d'une creation.
 */
@Component
public class ScopeResolver {

    private final CurrentUserProvider currentUser;
    private final EquipeRepository equipeRepository;

    public ScopeResolver(CurrentUserProvider currentUser, EquipeRepository equipeRepository) {
        this.currentUser = currentUser;
        this.equipeRepository = equipeRepository;
    }

    public Scope resolve() {
        Utilisateur u = currentUser.current();
        return switch (u.getRole()) {
            case SUPER_ADMIN -> Scope.tout();
            case PRESIDENT -> {
                if (u.getClubId() == null) yield Scope.aucun();
                List<UUID> ids = equipeRepository.findByClubId(u.getClubId())
                        .stream().map(Equipe::getId).toList();
                yield Scope.equipes(ids);
            }
            case ENTRAINEUR, PREPARATEUR, MEDICAL, JOUEUR ->
                    u.getEquipeId() != null ? Scope.equipes(List.of(u.getEquipeId())) : Scope.aucun();
            default -> Scope.aucun(); // ADMINISTRATIF : pas d'acces aux donnees
        };
    }

    /** Equipe a poser sur une donnee creee (l'equipe du staff connecte ; null pour super-admin). */
    public UUID equipePourEcriture() {
        return currentUser.current().getEquipeId();
    }

    /** L'equipe donnee est-elle dans la portee de l'utilisateur courant ? */
    public boolean peutAcceder(UUID equipeId) {
        Scope s = resolve();
        if (s.all()) return true;
        if (s.none()) return false;
        return equipeId != null && s.equipeIds().contains(equipeId);
    }

    /** Verifie l'acces a une ressource d'equipe ; 404 si hors perimetre (ne revele pas l'existence). */
    public void verifieAcces(UUID equipeId) {
        if (!peutAcceder(equipeId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ressource hors de votre perimetre");
        }
    }
}
