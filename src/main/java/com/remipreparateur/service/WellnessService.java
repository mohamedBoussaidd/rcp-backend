package com.remipreparateur.service;

import com.remipreparateur.dto.WellnessDtos.WellnessRequest;
import com.remipreparateur.dto.WellnessDtos.WellnessResponse;
import com.remipreparateur.entity.Joueur;
import com.remipreparateur.entity.WellnessQuotidien;
import com.remipreparateur.repository.JoueurRepository;
import com.remipreparateur.repository.WellnessQuotidienRepository;
import com.remipreparateur.security.CurrentUserProvider;
import com.remipreparateur.security.Scope;
import com.remipreparateur.security.ScopeResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ressenti quotidien (wellness) du joueur. Une saisie par jour (upsert sur joueur+date).
 * Lecture staff scopée à l'équipe (cf. {@link BlessureService}).
 */
@Service
public class WellnessService {

    private final WellnessQuotidienRepository repository;
    private final JoueurRepository joueurRepository;
    private final ScopeResolver scopeResolver;
    private final CurrentUserProvider currentUser;

    public WellnessService(WellnessQuotidienRepository repository, JoueurRepository joueurRepository,
                           ScopeResolver scopeResolver, CurrentUserProvider currentUser) {
        this.repository = repository;
        this.joueurRepository = joueurRepository;
        this.scopeResolver = scopeResolver;
        this.currentUser = currentUser;
    }

    public WellnessResponse enregistrer(UUID joueurId, WellnessRequest req) {
        LocalDate date = req.date() != null ? req.date() : LocalDate.now();
        Joueur joueur = joueurRepository.findById(joueurId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Fiche joueur introuvable"));

        WellnessQuotidien w = repository.findByJoueurIdAndDate(joueurId, date)
                .orElseGet(WellnessQuotidien::new);
        w.setJoueurId(joueurId);
        w.setEquipeId(joueur.getEquipeId());
        w.setDate(date);
        w.setSommeil(req.sommeil());
        w.setFatigue(req.fatigue());
        w.setDouleur(req.douleur());
        w.setStress(req.stress());
        w.setHumeur(req.humeur());
        w.setCommentaire(videEnNull(req.commentaire()));
        // Gêne optionnelle : si pas de zone, on efface tout le signalement.
        String zone = videEnNull(req.geneZone());
        w.setGeneZone(zone);
        w.setGeneIntensite(zone != null ? req.geneIntensite() : null);
        w.setGeneMoment(zone != null ? videEnNull(req.geneMoment()) : null);
        // Nouvelle saisie : la gêne (re)devient active (non traitée).
        w.setGeneTraitee(false);
        w.setGeneTraiteePar(null);
        w.setGeneTraiteeLe(null);
        w.setGeneResolution(null);
        return toResponse(repository.save(w), joueur);
    }

    public List<WellnessResponse> listerPourJoueur(UUID joueurId) {
        List<WellnessQuotidien> rows = repository.findByJoueurIdOrderByDateDesc(joueurId);
        Joueur joueur = joueurRepository.findById(joueurId).orElse(null);
        return rows.stream().map(w -> toResponse(w, joueur)).toList();
    }

    public List<WellnessResponse> listerPourStaff(UUID joueurId) {
        List<WellnessQuotidien> rows;
        if (joueurId != null) {
            rows = repository.findByJoueurIdOrderByDateDesc(joueurId).stream()
                    .filter(w -> scopeResolver.peutAcceder(w.getEquipeId()))
                    .toList();
        } else {
            Scope s = scopeResolver.resolve();
            if (s.all()) rows = repository.findAllByOrderByDateDesc();
            else if (s.none()) rows = List.of();
            else rows = repository.findByEquipeIdInOrderByDateDesc(s.equipeIds());
        }
        Map<UUID, Joueur> joueurs = joueurRepository.findAllById(
                        rows.stream().map(WellnessQuotidien::getJoueurId).distinct().toList())
                .stream().collect(Collectors.toMap(Joueur::getId, Function.identity()));
        return rows.stream().map(w -> toResponse(w, joueurs.get(w.getJoueurId()))).toList();
    }

    /**
     * Marque la gêne d'une saisie comme traitée (staff). Scopée à l'équipe.
     * {@code resolution} = ARCHIVEE (archivage simple) ou CONVERTIE (convertie en blessure).
     */
    public WellnessResponse traiterGene(UUID wellnessId, String resolution) {
        WellnessQuotidien w = repository.findById(wellnessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saisie introuvable"));
        scopeResolver.verifieAcces(w.getEquipeId());
        if (w.getGeneZone() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucune gêne à traiter");
        }
        String res = "CONVERTIE".equalsIgnoreCase(resolution) ? "CONVERTIE" : "ARCHIVEE";
        w.setGeneTraitee(true);
        w.setGeneTraiteePar(currentUser.current().getId());
        w.setGeneTraiteeLe(LocalDateTime.now());
        w.setGeneResolution(res);
        Joueur joueur = joueurRepository.findById(w.getJoueurId()).orElse(null);
        return toResponse(repository.save(w), joueur);
    }

    /**
     * Rouvre une gêne précédemment traitée (réservé MEDICAL/SUPER_ADMIN) : elle
     * redevient active et réapparaît dans les alertes, pour révision ou conversion
     * en blessure. Scopée à l'équipe.
     */
    public WellnessResponse rouvrirGene(UUID wellnessId) {
        WellnessQuotidien w = repository.findById(wellnessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saisie introuvable"));
        scopeResolver.verifieAcces(w.getEquipeId());
        if (w.getGeneZone() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucune gêne à rouvrir");
        }
        w.setGeneTraitee(false);
        w.setGeneTraiteePar(null);
        w.setGeneTraiteeLe(null);
        w.setGeneResolution(null);
        Joueur joueur = joueurRepository.findById(w.getJoueurId()).orElse(null);
        return toResponse(repository.save(w), joueur);
    }

    /**
     * Score de bien-être 0..100. Les items négatifs (fatigue, douleur, stress) sont inversés
     * pour que « plus haut = mieux ». Moyenne des 5 items (1..5) ramenée sur 100.
     */
    private int scoreBienEtre(WellnessQuotidien w) {
        int somme = w.getSommeil() + w.getHumeur()
                + (6 - w.getFatigue()) + (6 - w.getDouleur()) + (6 - w.getStress());
        return Math.round(somme / 5f * 20f);
    }

    private String videEnNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private WellnessResponse toResponse(WellnessQuotidien w, Joueur j) {
        return new WellnessResponse(
                w.getId(), w.getJoueurId(),
                j != null ? j.getNom() : null,
                j != null ? j.getPrenom() : null,
                w.getDate(), w.getSommeil(), w.getFatigue(), w.getDouleur(), w.getStress(), w.getHumeur(),
                scoreBienEtre(w), w.getCommentaire(),
                w.getGeneZone(), w.getGeneIntensite(), w.getGeneMoment(), w.isGeneTraitee(),
                w.getGeneResolution(), w.getGeneTraiteeLe(),
                w.getCreatedAt());
    }
}
