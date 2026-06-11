package com.remipreparateur.performance.rpe.service;

import com.remipreparateur.performance.rpe.dto.RpeDtos.RpeRequest;
import com.remipreparateur.performance.rpe.dto.RpeDtos.RpeResponse;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.performance.rpe.entity.RpeSeance;
import com.remipreparateur.performance.seance.entity.Seance;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.performance.rpe.repository.RpeSeanceRepository;
import com.remipreparateur.performance.seance.repository.SeanceRepository;
import com.remipreparateur.shared.security.Scope;
import com.remipreparateur.shared.security.ScopeResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * RPE de séance (effort perçu). Une saisie par séance (upsert sur joueur+séance).
 * La séance référencée est physique ({@code seance}) ou technique ({@code seance_technique})
 * selon {@code seanceType} ; date et équipe sont résolues depuis la séance.
 */
@Service
public class RpeService {

    private static final String TYPE_PHYSIQUE = "PHYSIQUE";
    private static final String TYPE_TECHNIQUE = "TECHNIQUE";

    private final RpeSeanceRepository repository;
    private final JoueurRepository joueurRepository;
    private final SeanceRepository seanceRepository;
    private final ScopeResolver scopeResolver;

    public RpeService(RpeSeanceRepository repository, JoueurRepository joueurRepository,
                      SeanceRepository seanceRepository,
                      ScopeResolver scopeResolver) {
        this.repository = repository;
        this.joueurRepository = joueurRepository;
        this.seanceRepository = seanceRepository;
        this.scopeResolver = scopeResolver;
    }

    public RpeResponse enregistrer(UUID joueurId, RpeRequest req) {
        // Séances unifiées : la RPE porte toujours sur une Seance. seanceType conservé en base
        // pour compat (défaut PHYSIQUE).
        String type = req.seanceType() == null || req.seanceType().isBlank()
                ? TYPE_PHYSIQUE : req.seanceType().trim().toUpperCase();
        Joueur joueur = joueurRepository.findById(joueurId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Fiche joueur introuvable"));

        // Résolution date + équipe + durée depuis la séance référencée.
        Seance s = seanceRepository.findById(req.seanceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Séance introuvable"));
        LocalDate date = s.getDate();
        UUID equipeSeance = s.getEquipeId();
        Short duree = req.dureeMinutes() != null ? req.dureeMinutes() : s.getDureeMinutes();

        // Le joueur ne peut noter qu'une séance de sa propre équipe.
        if (joueur.getEquipeId() == null || !Objects.equals(joueur.getEquipeId(), equipeSeance)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Séance hors de votre équipe");
        }

        RpeSeance r = repository.findByJoueurIdAndSeanceId(joueurId, req.seanceId())
                .orElseGet(RpeSeance::new);
        r.setJoueurId(joueurId);
        r.setEquipeId(equipeSeance);
        r.setSeanceId(req.seanceId());
        r.setSeanceType(type);
        r.setDate(date);
        r.setRpe(req.rpe());
        r.setDureeMinutes(duree);
        r.setCharge(duree != null ? req.rpe() * duree : null);
        r.setCommentaire(videEnNull(req.commentaire()));
        return toResponse(repository.save(r), joueur);
    }

    public List<RpeResponse> listerPourJoueur(UUID joueurId) {
        List<RpeSeance> rows = repository.findByJoueurIdOrderByDateDesc(joueurId);
        Joueur joueur = joueurRepository.findById(joueurId).orElse(null);
        return rows.stream().map(r -> toResponse(r, joueur)).toList();
    }

    public List<RpeResponse> listerPourStaff(UUID joueurId) {
        List<RpeSeance> rows;
        if (joueurId != null) {
            rows = repository.findByJoueurIdOrderByDateDesc(joueurId).stream()
                    .filter(r -> scopeResolver.peutAcceder(r.getEquipeId()))
                    .toList();
        } else {
            Scope s = scopeResolver.resolve();
            if (s.all()) rows = repository.findAllByOrderByDateDesc();
            else if (s.none()) rows = List.of();
            else rows = repository.findByEquipeIdInOrderByDateDesc(s.equipeIds());
        }
        Map<UUID, Joueur> joueurs = joueurRepository.findAllById(
                        rows.stream().map(RpeSeance::getJoueurId).distinct().toList())
                .stream().collect(Collectors.toMap(Joueur::getId, Function.identity()));
        return rows.stream().map(r -> toResponse(r, joueurs.get(r.getJoueurId()))).toList();
    }

    private String videEnNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private RpeResponse toResponse(RpeSeance r, Joueur j) {
        return new RpeResponse(
                r.getId(), r.getJoueurId(),
                j != null ? j.getNom() : null,
                j != null ? j.getPrenom() : null,
                r.getSeanceId(), r.getSeanceType(), r.getDate(),
                r.getRpe(), r.getDureeMinutes(), r.getCharge(),
                r.getCommentaire(), r.getCreatedAt());
    }
}
