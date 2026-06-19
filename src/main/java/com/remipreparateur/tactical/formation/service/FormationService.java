package com.remipreparateur.tactical.formation.service;

import com.remipreparateur.tactical.formation.dto.FormationDtos.FormationRequest;
import com.remipreparateur.tactical.formation.dto.FormationDtos.FormationResponse;
import com.remipreparateur.tactical.formation.entity.Formation;
import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.tactical.formation.repository.FormationRepository;
import com.remipreparateur.auth.repository.UtilisateurRepository;
import com.remipreparateur.auth.rbac.PermissionResolver;
import com.remipreparateur.shared.security.ContexteActif;
import com.remipreparateur.shared.security.ContexteActifHolder;
import com.remipreparateur.shared.security.CurrentUserProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/** Formations tactiques personnalisées, partagées au sein d'un club. */
@Service
public class FormationService {

    private final FormationRepository formationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final CurrentUserProvider currentUser;
    private final PermissionResolver permissionResolver;

    public FormationService(FormationRepository formationRepository,
                            UtilisateurRepository utilisateurRepository,
                            CurrentUserProvider currentUser,
                            PermissionResolver permissionResolver) {
        this.formationRepository = formationRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.currentUser = currentUser;
        this.permissionResolver = permissionResolver;
    }

    public List<FormationResponse> lister() {
        Utilisateur u = currentUser.current();
        UUID clubId = clubCourant(u);
        boolean peutEcrire = peutEcrire(u);
        List<Formation> formations = (clubId != null)
                ? formationRepository.findByClubIdOrderByCreatedAtDesc(clubId)
                : (u.getRole() == Role.SUPER_ADMIN ? formationRepository.findAll() : List.of());
        return formations.stream().map(f -> toResponse(f, peutEcrire)).toList();
    }

    public FormationResponse creer(FormationRequest req) {
        Utilisateur u = currentUser.current();
        UUID clubId = clubCourant(u);
        if (clubId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Aucun club actif");
        }
        Formation f = new Formation();
        f.setClubId(clubId);
        f.setCreePar(u.getId());
        f.setNom(req.nom());
        f.setCouleur(req.couleur());
        f.setPositionsJson(req.positionsJson());
        return toResponse(formationRepository.save(f), peutEcrire(u));
    }

    public void supprimer(UUID id) {
        Utilisateur u = currentUser.current();
        Formation f = formationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Formation introuvable"));
        UUID club = clubCourant(u);
        boolean memeClub = u.getRole() == Role.SUPER_ADMIN || (club != null && club.equals(f.getClubId()));
        if (!memeClub || !peutEcrire(u)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Action non autorisée sur cette formation");
        }
        formationRepository.deleteById(id);
    }

    /** Club courant : club du contexte actif pour le super-admin, sinon club identité. */
    private UUID clubCourant(Utilisateur u) {
        if (u.getRole() == Role.SUPER_ADMIN) {
            ContexteActif ctx = ContexteActifHolder.get();
            return ctx != null ? ctx.clubId() : null;
        }
        return u.getClubId();
    }

    private boolean peutEcrire(Utilisateur u) {
        return u.getRole() == Role.SUPER_ADMIN || permissionResolver.permissionsPour(u).contains("formations:write");
    }

    private FormationResponse toResponse(Formation f, boolean peutEcrire) {
        String creeParNom = f.getCreePar() != null
                ? utilisateurRepository.findById(f.getCreePar())
                    .map(c -> ((c.getPrenom() != null ? c.getPrenom() + " " : "") + (c.getNom() != null ? c.getNom() : "")).trim())
                    .orElse(null)
                : null;
        return new FormationResponse(f.getId(), f.getNom(), f.getCouleur(), f.getPositionsJson(), creeParNom, peutEcrire);
    }
}
