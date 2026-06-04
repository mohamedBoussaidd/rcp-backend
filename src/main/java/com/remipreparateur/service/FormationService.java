package com.remipreparateur.service;

import com.remipreparateur.dto.FormationDtos.FormationRequest;
import com.remipreparateur.dto.FormationDtos.FormationResponse;
import com.remipreparateur.entity.Formation;
import com.remipreparateur.entity.Utilisateur;
import com.remipreparateur.repository.FormationRepository;
import com.remipreparateur.repository.UtilisateurRepository;
import com.remipreparateur.security.CurrentUserProvider;
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

    public FormationService(FormationRepository formationRepository,
                            UtilisateurRepository utilisateurRepository,
                            CurrentUserProvider currentUser) {
        this.formationRepository = formationRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.currentUser = currentUser;
    }

    public List<FormationResponse> lister() {
        Utilisateur u = currentUser.current();
        List<Formation> formations = (u.getRole() == com.remipreparateur.entity.Role.SUPER_ADMIN)
                ? formationRepository.findAll()
                : (u.getClubId() != null ? formationRepository.findByClubIdOrderByCreatedAtDesc(u.getClubId()) : List.of());
        return formations.stream().map(f -> toResponse(f, u)).toList();
    }

    public FormationResponse creer(FormationRequest req) {
        Utilisateur u = currentUser.current();
        if (u.getClubId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Aucun club associe au compte");
        }
        Formation f = new Formation();
        f.setClubId(u.getClubId());
        f.setCreePar(u.getId());
        f.setNom(req.nom());
        f.setCouleur(req.couleur());
        f.setPositionsJson(req.positionsJson());
        return toResponse(formationRepository.save(f), u);
    }

    public void supprimer(UUID id) {
        Utilisateur u = currentUser.current();
        Formation f = formationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Formation introuvable"));
        if (!peutModifier(f, u)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seul le createur (ou le president) peut supprimer cette formation");
        }
        formationRepository.deleteById(id);
    }

    private boolean peutModifier(Formation f, Utilisateur u) {
        return switch (u.getRole()) {
            case SUPER_ADMIN -> true;
            case PRESIDENT -> u.getClubId() != null && u.getClubId().equals(f.getClubId());
            default -> f.getCreePar() != null && f.getCreePar().equals(u.getId());
        };
    }

    private FormationResponse toResponse(Formation f, Utilisateur courant) {
        String creeParNom = f.getCreePar() != null
                ? utilisateurRepository.findById(f.getCreePar())
                    .map(c -> ((c.getPrenom() != null ? c.getPrenom() + " " : "") + (c.getNom() != null ? c.getNom() : "")).trim())
                    .orElse(null)
                : null;
        return new FormationResponse(f.getId(), f.getNom(), f.getCouleur(), f.getPositionsJson(), creeParNom, peutModifier(f, courant));
    }
}
