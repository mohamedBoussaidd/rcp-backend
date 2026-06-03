package com.remipreparateur.service;

import com.remipreparateur.dto.BlessureDtos.BlessureRequest;
import com.remipreparateur.dto.BlessureDtos.BlessureResponse;
import com.remipreparateur.entity.Blessure;
import com.remipreparateur.entity.Joueur;
import com.remipreparateur.repository.BlessureRepository;
import com.remipreparateur.repository.JoueurRepository;
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

    public BlessureService(BlessureRepository blessureRepository, JoueurRepository joueurRepository) {
        this.blessureRepository = blessureRepository;
        this.joueurRepository = joueurRepository;
    }

    public List<BlessureResponse> lister(UUID joueurId) {
        List<Blessure> blessures = (joueurId != null)
                ? blessureRepository.findByJoueurIdOrderByDateBlessureDesc(joueurId)
                : blessureRepository.findAllByOrderByDateBlessureDesc();

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
        // equipe_id laisse null tant que le scoping par equipe n'est pas active (cf. plan)
        return toResponse(blessureRepository.save(b), joueur);
    }

    public BlessureResponse modifier(UUID id, BlessureRequest req) {
        Blessure b = blessureRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blessure introuvable"));
        appliquer(b, req);
        Joueur joueur = joueurRepository.findById(b.getJoueurId()).orElse(null);
        return toResponse(blessureRepository.save(b), joueur);
    }

    public void supprimer(UUID id) {
        if (!blessureRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Blessure introuvable");
        }
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
