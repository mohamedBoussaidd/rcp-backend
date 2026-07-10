package com.remipreparateur.shared.security;

import com.remipreparateur.club.entity.Equipe;
import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.club.repository.EquipeRepository;
import com.remipreparateur.saison.service.AppartenanceService;
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
    private final AppartenanceService appartenance;

    public ScopeResolver(CurrentUserProvider currentUser, EquipeRepository equipeRepository,
                         AppartenanceService appartenance) {
        this.currentUser = currentUser;
        this.equipeRepository = equipeRepository;
        this.appartenance = appartenance;
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
            // PRESIDENT et ADMINISTRATIF pilotent le CLUB entier, pas une équipe précise : leur
            // portée = toutes les équipes du club. ADMINISTRATIF n'obtient aucune donnée sportive/
            // médicale pour autant — le filtre par Permission (hasAuthority) reste seul décisif ;
            // il ne détient aujourd'hui QUE les permissions docadmin:* (cf. V47).
            case PRESIDENT, ADMINISTRATIF -> {
                if (u.getClubId() == null) yield Scope.aucun();
                List<UUID> ids = equipeRepository.findByClubId(u.getClubId())
                        .stream().map(Equipe::getId).toList();
                yield Scope.equipes(ids);
            }
            case ENTRAINEUR, PREPARATEUR, MEDICAL, JOUEUR ->
                    u.getEquipeId() != null ? Scope.equipes(List.of(u.getEquipeId())) : Scope.aucun();
            default -> Scope.aucun();
        };
    }

    /** Equipe a poser sur une donnee creee (l'equipe du staff connecte ; null pour super-admin). */
    public UUID equipePourEcriture() {
        return currentUser.current().getEquipeId();
    }

    /**
     * Club dont l'utilisateur gère l'INTÉGRALITÉ des fiches (y compris celles non assignées à une
     * équipe) : président / administratif → leur club ; super-admin → club du contexte actif.
     * {@code null} pour les rôles scopés équipe (ils ne voient que les fiches de leurs équipes) et
     * pour le super-admin hors contexte (il voit tout, géré en amont via {@link Scope#all()}).
     */
    public UUID clubEntierPourGestion() {
        Utilisateur u = currentUser.current();
        return switch (u.getRole()) {
            case PRESIDENT, ADMINISTRATIF -> u.getClubId();
            case SUPER_ADMIN -> {
                ContexteActif ctx = ContexteActifHolder.get();
                yield ctx != null ? ctx.clubId() : null;
            }
            default -> null;
        };
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

    /**
     * Vérifie l'accès à une FICHE (personne), qui peut être assignée à une équipe OU au niveau club
     * (non assignée). Fiche assignée → contrôle d'équipe strict. Fiche non assignée → réservée au
     * super-admin et aux rôles gérant le club entier (président / administratif de CE club).
     */
    public void verifieAccesFiche(UUID equipeId, UUID clubId) {
        if (equipeId != null) {
            verifieAcces(equipeId);
            return;
        }
        Utilisateur u = currentUser.current();
        if (u.getRole() == Role.SUPER_ADMIN) return;
        UUID clubGere = clubEntierPourGestion();
        if (clubGere != null && clubGere.equals(clubId)) return;
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ressource hors de votre perimetre");
    }

    /**
     * Vérifie l'accès à une PERSONNE (fiche) sans dépendre du cache {@code joueur.equipe_id} : son
     * appartenance est dérivée de l'effectif (Phase 4). Fiche assignée → OK si AU MOINS une de ses
     * équipes est dans la portée ; fiche « pool » (aucune équipe) ou hors portée → réservée au
     * super-admin et aux rôles gérant le club entier de CE club (comportement {@link #verifieAccesFiche}).
     */
    public void verifieAccesPersonne(UUID joueurId, UUID clubId) {
        for (UUID equipeId : appartenance.equipesDe(joueurId)) {
            if (peutAcceder(equipeId)) return;
        }
        Utilisateur u = currentUser.current();
        if (u.getRole() == Role.SUPER_ADMIN) return;
        UUID clubGere = clubEntierPourGestion();
        if (clubGere != null && clubGere.equals(clubId)) return;
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ressource hors de votre perimetre");
    }

    /**
     * Le club donné est-il accessible à l'utilisateur ? Accès au niveau CLUB (indépendant des
     * équipes) : un président/administratif accède à SON club même s'il n'a encore aucune équipe
     * (club neuf) ; un super-admin accède au club de son contexte ; un staff équipe-scopé accède
     * au club d'une de ses équipes.
     */
    public boolean peutAccederClub(UUID clubId) {
        if (clubId == null) return false;
        Utilisateur u = currentUser.current();
        if (u.getRole() == Role.SUPER_ADMIN) {
            ContexteActif ctx = ContexteActifHolder.get();
            return ctx == null || ctx.clubId() == null || clubId.equals(ctx.clubId());
        }
        // Club de rattachement (président/administratif) : accessible même sans équipe.
        if (clubId.equals(u.getClubId())) return true;
        Scope s = resolve();
        if (s.all()) return true;
        if (s.none()) return false;
        return equipeRepository.findAllById(s.equipeIds()).stream()
                .anyMatch(e -> clubId.equals(e.getClubId()));
    }

    /** Verifie l'acces a une ressource de club ; 404 si hors perimetre. */
    public void verifieAccesClub(UUID clubId) {
        if (!peutAccederClub(clubId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ressource hors de votre perimetre");
        }
    }
}
