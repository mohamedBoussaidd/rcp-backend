package com.remipreparateur.medical.conseil.service;

import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.medical.conseil.dto.ConseilDtos.ConseilRequest;
import com.remipreparateur.medical.conseil.dto.ConseilDtos.ConseilResponse;
import com.remipreparateur.medical.conseil.entity.ConseilStaff;
import com.remipreparateur.medical.conseil.repository.ConseilStaffRepository;
import com.remipreparateur.saison.service.AppartenanceService;
import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.shared.security.ScopeResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Conseils du staff (médical / préparateur) au joueur. Conseil d'équipe (joueurId null)
 * ou personnel. Écriture/lecture staff scopées à l'équipe via {@link ScopeResolver} ;
 * lecture joueur cantonnée à son équipe + ses conseils perso.
 */
@Service
public class ConseilService {

    private final ConseilStaffRepository repository;
    private final JoueurRepository joueurRepository;
    private final ScopeResolver scopeResolver;
    private final CurrentUserProvider currentUser;
    private final AppartenanceService appartenance;

    public ConseilService(ConseilStaffRepository repository, JoueurRepository joueurRepository,
                          ScopeResolver scopeResolver, CurrentUserProvider currentUser,
                          AppartenanceService appartenance) {
        this.repository = repository;
        this.joueurRepository = joueurRepository;
        this.scopeResolver = scopeResolver;
        this.currentUser = currentUser;
        this.appartenance = appartenance;
    }

    // ──────────────────────────── Staff ────────────────────────────

    /**
     * Conseils visibles par le staff : conseils d'équipe + (si {@code joueurId} fourni)
     * conseils personnels de ce joueur. Sans joueurId, seuls les conseils d'équipe.
     */
    public List<ConseilResponse> listerPourStaff(UUID joueurId) {
        if (joueurId != null) {
            Joueur joueur = joueurChecke(joueurId);
            return repository.findByEquipeIdAndJoueurIdIsNullOrJoueurIdOrderByCreatedAtDesc(
                            appartenance.equipePrincipale(joueurId), joueurId).stream()
                    .map(c -> toResponse(c, joueur))
                    .toList();
        }
        UUID equipe = scopeResolver.equipeActiveUnique();
        return repository.findByEquipeIdAndJoueurIdIsNullOrderByCreatedAtDesc(equipe).stream()
                .map(c -> toResponse(c, null))
                .toList();
    }

    public ConseilResponse creer(ConseilRequest req) {
        ConseilStaff c = new ConseilStaff();
        Joueur joueur = appliquer(c, req);
        c.setCreePar(currentUser.current().getId());
        c.setUpdatedAt(LocalDateTime.now());
        return toResponse(repository.save(c), joueur);
    }

    public ConseilResponse modifier(UUID id, ConseilRequest req) {
        ConseilStaff c = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conseil introuvable"));
        scopeResolver.verifieAcces(c.getEquipeId());
        Joueur joueur = appliquer(c, req);
        c.setUpdatedAt(LocalDateTime.now());
        return toResponse(repository.save(c), joueur);
    }

    public void supprimer(UUID id) {
        ConseilStaff c = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conseil introuvable"));
        scopeResolver.verifieAcces(c.getEquipeId());
        repository.delete(c);
    }

    // ──────────────────────────── Joueur ────────────────────────────

    /** Conseils visibles par le joueur : ceux de son équipe + ses conseils personnels. */
    public List<ConseilResponse> listerPourJoueur(UUID joueurId) {
        Joueur joueur = joueurRepository.findById(joueurId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Fiche joueur introuvable"));
        return repository.findByEquipeIdAndJoueurIdIsNullOrJoueurIdOrderByCreatedAtDesc(
                        appartenance.equipePrincipale(joueurId), joueurId).stream()
                .map(c -> toResponse(c, joueur))
                .toList();
    }

    // ──────────────────────────── Interne ────────────────────────────

    /** Renseigne l'entité depuis la requête (cible + contenu) et résout l'équipe. */
    private Joueur appliquer(ConseilStaff c, ConseilRequest req) {
        Joueur joueur = null;
        if (req.joueurId() != null) {
            joueur = joueurChecke(req.joueurId());
            c.setJoueurId(joueur.getId());
            c.setEquipeId(appartenance.equipePrincipale(joueur.getId()));
        } else {
            c.setJoueurId(null);
            c.setEquipeId(scopeResolver.equipeActiveUnique());
        }
        c.setTitre(req.titre().trim());
        c.setTexte(req.texte().trim());
        c.setIcone(videEnNull(req.icone()));
        return joueur;
    }

    /** Charge un joueur et vérifie qu'il est dans le périmètre du staff. */
    private Joueur joueurChecke(UUID joueurId) {
        Joueur joueur = joueurRepository.findById(joueurId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Joueur introuvable"));
        scopeResolver.verifieAccesPersonne(joueur.getId(), joueur.getClubId());
        return joueur;
    }

    private String videEnNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private ConseilResponse toResponse(ConseilStaff c, Joueur joueur) {
        Joueur j = joueur;
        if (j == null && c.getJoueurId() != null) {
            j = joueurRepository.findById(c.getJoueurId()).orElse(null);
        }
        return new ConseilResponse(
                c.getId(), c.getEquipeId(), c.getJoueurId(),
                j != null ? j.getNom() : null,
                j != null ? j.getPrenom() : null,
                c.getTitre(), c.getTexte(), c.getIcone(),
                c.getJoueurId() == null,
                c.getCreatedAt(), c.getUpdatedAt());
    }
}
