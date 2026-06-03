package com.remipreparateur.service;

import com.remipreparateur.dto.BlessureDtos.BlessureRequest;
import com.remipreparateur.dto.BlessureDtos.BlessureResponse;
import com.remipreparateur.entity.Blessure;
import com.remipreparateur.entity.Joueur;
import com.remipreparateur.repository.BlessureRepository;
import com.remipreparateur.repository.JoueurRepository;
import com.remipreparateur.security.Scope;
import com.remipreparateur.security.ScopeResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BlessureService {

    private final BlessureRepository blessureRepository;
    private final JoueurRepository joueurRepository;
    private final ScopeResolver scopeResolver;

    public BlessureService(BlessureRepository blessureRepository, JoueurRepository joueurRepository,
                           ScopeResolver scopeResolver) {
        this.blessureRepository = blessureRepository;
        this.joueurRepository = joueurRepository;
        this.scopeResolver = scopeResolver;
    }

    public List<BlessureResponse> lister(UUID joueurId) {
        List<Blessure> blessures;
        if (joueurId != null) {
            blessures = blessureRepository.findByJoueurIdOrderByDateBlessureDesc(joueurId);
        } else {
            Scope s = scopeResolver.resolve();
            if (s.all()) blessures = blessureRepository.findAllByOrderByDateBlessureDesc();
            else if (s.none()) blessures = List.of();
            else blessures = blessureRepository.findByEquipeIdInOrderByDateBlessureDesc(s.equipeIds());
        }

        Map<UUID, Joueur> joueurs = joueurRepository.findAllById(
                        blessures.stream().map(Blessure::getJoueurId).distinct().toList())
                .stream().collect(Collectors.toMap(Joueur::getId, Function.identity()));

        return blessures.stream().map(b -> toResponse(b, joueurs.get(b.getJoueurId()))).toList();
    }

    public BlessureResponse creer(BlessureRequest req) {
        Joueur joueur = joueurRepository.findById(req.joueurId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Joueur introuvable"));
        Blessure b = new Blessure();
        appliquer(b, req);
        b.setEquipeId(joueur.getEquipeId()); // rattache la blessure a l'equipe du joueur
        return toResponse(blessureRepository.save(b), joueur);
    }

    public BlessureResponse modifier(UUID id, BlessureRequest req) {
        Blessure b = blessureRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blessure introuvable"));
        scopeResolver.verifieAcces(b.getEquipeId());
        appliquer(b, req);
        Joueur joueur = joueurRepository.findById(b.getJoueurId()).orElse(null);
        return toResponse(blessureRepository.save(b), joueur);
    }

    public void supprimer(UUID id) {
        Blessure b = blessureRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blessure introuvable"));
        scopeResolver.verifieAcces(b.getEquipeId());
        blessureRepository.deleteById(id);
    }

    private void appliquer(Blessure b, BlessureRequest req) {
        b.setJoueurId(req.joueurId());
        b.setDateBlessure(req.dateBlessure());
        b.setDateRetourEffectif(req.dateRetourEffectif());
        b.setTypeBlessure(req.typeBlessure());
        b.setZoneCorporelle(req.zoneCorporelle());
        b.setCote(req.cote());
        b.setGravite(req.gravite());
        b.setCauseProbable(req.causeProbable());
        b.setRecidive(Boolean.TRUE.equals(req.recidive()));
        b.setCommentaire(req.commentaire());
    }

    private BlessureResponse toResponse(Blessure b, Joueur j) {
        boolean enCours = b.getDateRetourEffectif() == null
                || b.getDateRetourEffectif().isAfter(LocalDate.now());
        return new BlessureResponse(
                b.getId(), b.getJoueurId(),
                j != null ? j.getNom() : null,
                j != null ? j.getPrenom() : null,
                b.getDateBlessure(), b.getDateRetourEffectif(),
                b.getTypeBlessure(), b.getZoneCorporelle(), b.getCote(),
                b.getGravite(), b.getCauseProbable(), b.isRecidive(),
                b.getCommentaire(), enCours);
    }
}
