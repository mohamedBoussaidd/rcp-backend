package com.remipreparateur.tactical.exercice.service;

import com.remipreparateur.tactical.exercice.dto.ExerciceDtos.ExerciceRequest;
import com.remipreparateur.tactical.exercice.dto.ExerciceDtos.ExerciceResponse;
import com.remipreparateur.club.entity.Equipe;
import com.remipreparateur.tactical.exercice.entity.Exercice;
import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.club.repository.EquipeRepository;
import com.remipreparateur.tactical.exercice.repository.ExerciceRepository;
import com.remipreparateur.auth.repository.UtilisateurRepository;
import com.remipreparateur.shared.security.ContexteActif;
import com.remipreparateur.shared.security.ContexteActifHolder;
import com.remipreparateur.shared.security.CurrentUserProvider;
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
        UUID clubId = clubCourant(u);
        List<Exercice> exercices = (clubId != null)
                ? exerciceRepository.findByClubIdOrderByCreatedAtDesc(clubId)
                // Super-admin sans club actif (espace admin) : tout ; autres rôles sans club : rien.
                : (u.getRole() == Role.SUPER_ADMIN ? exerciceRepository.findAll() : List.of());
        return exercices.stream().map(e -> toResponse(e, u)).toList();
    }

    public ExerciceResponse creer(ExerciceRequest req) {
        Utilisateur u = currentUser.current();
        UUID clubId = clubCourant(u);
        if (clubId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Aucun club actif");
        }
        Exercice e = new Exercice();
        e.setClubId(clubId);
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

    /** Sauvegarde du schéma tactique (même droit que l'édition de l'exercice). */
    public ExerciceResponse modifierSchema(UUID id, String schemaJson) {
        Utilisateur u = currentUser.current();
        Exercice e = charge(id);
        exigeDroit(e, u);
        e.setSchemaJson(schemaJson);
        return toResponse(exerciceRepository.save(e), u);
    }

    // ── Helpers ──

    /**
     * Club dont dépend la bibliothèque. Super-admin : le club du contexte actif
     * (null s'il est sur l'espace admin sans club). Autres rôles : leur propre club
     * (le contexte ne peut pas changer leur club).
     */
    private UUID clubCourant(Utilisateur u) {
        if (u.getRole() == Role.SUPER_ADMIN) {
            ContexteActif ctx = ContexteActifHolder.get();
            return ctx != null ? ctx.clubId() : null;
        }
        return u.getClubId();
    }

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
                e.getIntensite(), e.getDescription(), e.getSchemaJson(),
                e.getCreePar(), creeParNom, e.getEquipeOrigineId(), equipeNom,
                peutModifier(e, courant));
    }
}
