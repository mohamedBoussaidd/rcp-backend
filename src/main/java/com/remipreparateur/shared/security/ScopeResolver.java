package com.remipreparateur.shared.security;

import com.remipreparateur.club.entity.Equipe;
import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.club.repository.EquipeRepository;
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

    /**
     * Portée effective = portée autorisée par l'identité, restreinte par le contexte
     * de navigation actif s'il y en a un (cf. {@link ContexteActif}). Le contexte ne
     * peut que réduire : toute équipe demandée hors de la portée autorisée → 403.
     */
    public Scope resolve() {
        Scope autorise = scopeIdentite();
        ContexteActif ctx = ContexteActifHolder.get();
        if (ctx == null || ctx.estVide()) {
            return autorise; // pas de contexte → comportement historique
        }

        // Équipes demandées : liste explicite, sinon toutes les équipes du club actif.
        List<UUID> demande;
        if (!ctx.equipeIds().isEmpty()) {
            demande = ctx.equipeIds();
        } else {
            demande = equipeRepository.findByClubId(ctx.clubId())
                    .stream().map(Equipe::getId).toList();
        }

        if (autorise.all()) {
            // Super-admin : aucune restriction d'identité, le contexte fixe le périmètre.
            return Scope.equipes(demande);
        }
        // Non super-admin : le contexte doit rester INCLUS dans la portée autorisée.
        for (UUID id : demande) {
            if (!autorise.equipeIds().contains(id)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Contexte hors de votre périmètre");
            }
        }
        return demande.isEmpty() ? autorise : Scope.equipes(demande);
    }

    /** Portée brute déduite de la seule identité (rôle + rattachements), sans contexte. */
    private Scope scopeIdentite() {
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

    /**
     * Club actif : contexte de navigation s'il en fixe un, sinon le club de l'utilisateur
     * (directement, ou déduit de son équipe). 409 si indéterminé (ex. super-admin sans contexte).
     */
    public UUID clubActif() {
        ContexteActif ctx = ContexteActifHolder.get();
        if (ctx != null && ctx.clubId() != null) {
            return ctx.clubId();
        }
        Utilisateur u = currentUser.current();
        if (u.getClubId() != null) {
            return u.getClubId();
        }
        if (u.getEquipeId() != null) {
            UUID club = equipeRepository.findById(u.getEquipeId())
                    .map(Equipe::getClubId).orElse(null);
            if (club != null) return club;
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Aucun club actif — sélectionnez un club");
    }

    /**
     * L'unique équipe active, pour les ressources « 1 par équipe » (plan de jeu, matchs…).
     * Le staff a son équipe ; président/super-admin doivent cibler UNE équipe via le contexte.
     * 409 si le périmètre n'est pas réduit à une seule équipe.
     */
    public UUID equipeActiveUnique() {
        Scope s = resolve();
        if (!s.all() && s.equipeIds().size() == 1) {
            return s.equipeIds().get(0);
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                s.none() ? "Aucune équipe active" : "Sélectionnez une équipe");
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
