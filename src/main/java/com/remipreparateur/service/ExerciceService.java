package com.remipreparateur.service;

import com.remipreparateur.dto.ExerciceDtos.ExerciceRequest;
import com.remipreparateur.dto.ExerciceDtos.ExerciceResponse;
import com.remipreparateur.entity.Equipe;
import com.remipreparateur.entity.Exercice;
import com.remipreparateur.entity.Role;
import com.remipreparateur.entity.Utilisateur;
import com.remipreparateur.repository.EquipeRepository;
import com.remipreparateur.repository.ExerciceRepository;
import com.remipreparateur.repository.UtilisateurRepository;
import com.remipreparateur.security.CurrentUserProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/** Bibliotheque d'exercices, partagee au sein d'un club. */
@Service
public class ExerciceService {

    private final ExerciceRepository exerciceRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final EquipeRepository equipeRepository;
    private final CurrentUserProvider currentUser;

    public ExerciceService(ExerciceRepository exerciceRepository,
                           UtilisateurRepository utilisateurRepository,
                           EquipeRepository equipeRepository,
                           CurrentUserProvider currentUser) {
        this.exerciceRepository = exerciceRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.equipeRepository = equipeRepository;
        this.currentUser = currentUser;
    }

    public List<ExerciceResponse> lister() {
        Utilisateur u = currentUser.current();
        List<Exercice> exercices = (u.getRole() == Role.SUPER_ADMIN)
                ? exerciceRepository.findAll()
                : (u.getClubId() != null
                    ? exerciceRepository.findByClubIdOrderByCreatedAtDesc(u.getClubId())
                    : List.of());
        return exercices.stream().map(e -> toResponse(e, u)).toList();
    }

    public ExerciceResponse creer(ExerciceRequest req) {
        Utilisateur u = currentUser.current();
        if (u.getClubId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Aucun club associe au compte");
        }
        Exercice e = new Exercice();
        e.setClubId(u.getClubId());
        e.setCreePar(u.getId());
        e.setEquipeOrigineId(u.getEquipeId());
        appliquer(e, req);
        return toResponse(exerciceRepository.save(e), u);
    }

    public ExerciceResponse modifier(UUID id, ExerciceRequest req) {
        Utilisateur u = currentUser.current();
        Exercice e = charge(id);
        exigeDroit(e, u);
        appliquer(e, req);
        return toResponse(exerciceRepository.save(e), u);
    }

    public void supprimer(UUID id) {
        Utilisateur u = currentUser.current();
        Exercice e = charge(id);
        exigeDroit(e, u);
        exerciceRepository.deleteById(id);
    }

    // ── Helpers ──
    private Exercice charge(UUID id) {
        return exerciceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercice introuvable"));
    }

    /** Edition/suppression : createur, ou president/super-admin du club. */
    private void exigeDroit(Exercice e, Utilisateur u) {
        if (!peutModifier(e, u)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seul le createur (ou le president) peut modifier cet exercice");
        }
    }

    private boolean peutModifier(Exercice e, Utilisateur u) {
        return switch (u.getRole()) {
            case SUPER_ADMIN -> true;
            case PRESIDENT -> u.getClubId() != null && u.getClubId().equals(e.getClubId());
            default -> e.getCreePar() != null && e.getCreePar().equals(u.getId());
        };
    }

    private void appliquer(Exercice e, ExerciceRequest req) {
        e.setNom(req.nom());
        e.setCategorie(req.categorie());
        e.setDureeMinutes(req.dureeMinutes());
        e.setObjectif(req.objectif());
        if (req.intensite() != null && (req.intensite() < 1 || req.intensite() > 5)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Intensite attendue entre 1 et 5");
        }
        e.setIntensite(req.intensite());
        e.setDescription(req.description());
    }

    private ExerciceResponse toResponse(Exercice e, Utilisateur courant) {
        String creeParNom = e.getCreePar() != null
                ? utilisateurRepository.findById(e.getCreePar())
                    .map(c -> ((c.getPrenom() != null ? c.getPrenom() + " " : "") + (c.getNom() != null ? c.getNom() : "")).trim())
                    .orElse(null)
                : null;
        String equipeNom = e.getEquipeOrigineId() != null
                ? equipeRepository.findById(e.getEquipeOrigineId()).map(Equipe::getNom).orElse(null)
                : null;
        return new ExerciceResponse(
                e.getId(), e.getNom(), e.getCategorie(), e.getDureeMinutes(), e.getObjectif(),
                e.getIntensite(), e.getDescription(),
                e.getCreePar(), creeParNom, e.getEquipeOrigineId(), equipeNom,
                peutModifier(e, courant));
    }
}
