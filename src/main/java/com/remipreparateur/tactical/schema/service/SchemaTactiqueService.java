package com.remipreparateur.tactical.schema.service;

import com.remipreparateur.tactical.schema.dto.SchemaTactiqueDtos.SchemaTactiqueRequest;
import com.remipreparateur.tactical.schema.dto.SchemaTactiqueDtos.SchemaTactiqueResponse;
import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.tactical.schema.entity.SchemaTactique;
import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.tactical.schema.repository.SchemaTactiqueRepository;
import com.remipreparateur.auth.repository.UtilisateurRepository;
import com.remipreparateur.shared.security.ContexteActif;
import com.remipreparateur.shared.security.ContexteActifHolder;
import com.remipreparateur.shared.security.CurrentUserProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Schémas tactiques réutilisables (bibliothèque), partagés au sein d'un club. */
@Service
public class SchemaTactiqueService {

    private final SchemaTactiqueRepository schemaRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final CurrentUserProvider currentUser;

    public SchemaTactiqueService(SchemaTactiqueRepository schemaRepository,
                                 UtilisateurRepository utilisateurRepository,
                                 CurrentUserProvider currentUser) {
        this.schemaRepository = schemaRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.currentUser = currentUser;
    }

    public List<SchemaTactiqueResponse> lister() {
        Utilisateur u = currentUser.current();
        UUID clubId = clubCourant(u);
        List<SchemaTactique> schemas = (clubId != null)
                ? schemaRepository.findByClubIdOrderByUpdatedAtDesc(clubId)
                : (u.getRole() == Role.SUPER_ADMIN ? schemaRepository.findAll() : List.of());
        return schemas.stream().map(s -> toResponse(s, u)).toList();
    }

    public SchemaTactiqueResponse creer(SchemaTactiqueRequest req) {
        Utilisateur u = currentUser.current();
        UUID clubId = clubCourant(u);
        if (clubId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Aucun club actif");
        }
        SchemaTactique s = new SchemaTactique();
        s.setClubId(clubId);
        s.setCreePar(u.getId());
        s.setNom(req.nom());
        s.setCategorie(req.categorie());
        s.setSchemaJson(req.schemaJson());
        s.setApercu(req.apercu());
        return toResponse(schemaRepository.save(s), u);
    }

    public SchemaTactiqueResponse modifier(UUID id, SchemaTactiqueRequest req) {
        Utilisateur u = currentUser.current();
        SchemaTactique s = schemaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schéma introuvable"));
        if (!peutModifier(s, u)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seul le createur (ou le president) peut modifier ce schéma");
        }
        s.setNom(req.nom());
        s.setCategorie(req.categorie());
        s.setSchemaJson(req.schemaJson());
        s.setApercu(req.apercu());
        s.setUpdatedAt(LocalDateTime.now());
        return toResponse(schemaRepository.save(s), u);
    }

    public void supprimer(UUID id) {
        Utilisateur u = currentUser.current();
        SchemaTactique s = schemaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schéma introuvable"));
        if (!peutModifier(s, u)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seul le createur (ou le president) peut supprimer ce schéma");
        }
        schemaRepository.deleteById(id);
    }

    /** Club courant : club du contexte actif pour le super-admin, sinon club identité. */
    private UUID clubCourant(Utilisateur u) {
        if (u.getRole() == Role.SUPER_ADMIN) {
            ContexteActif ctx = ContexteActifHolder.get();
            return ctx != null ? ctx.clubId() : null;
        }
        return u.getClubId();
    }

    private boolean peutModifier(SchemaTactique s, Utilisateur u) {
        return switch (u.getRole()) {
            case SUPER_ADMIN -> true;
            case PRESIDENT -> u.getClubId() != null && u.getClubId().equals(s.getClubId());
            default -> s.getCreePar() != null && s.getCreePar().equals(u.getId());
        };
    }

    private SchemaTactiqueResponse toResponse(SchemaTactique s, Utilisateur courant) {
        String creeParNom = s.getCreePar() != null
                ? utilisateurRepository.findById(s.getCreePar())
                    .map(c -> ((c.getPrenom() != null ? c.getPrenom() + " " : "") + (c.getNom() != null ? c.getNom() : "")).trim())
                    .orElse(null)
                : null;
        return new SchemaTactiqueResponse(s.getId(), s.getNom(), s.getCategorie(), s.getSchemaJson(),
                s.getApercu(), creeParNom, s.getUpdatedAt(), peutModifier(s, courant));
    }
}
