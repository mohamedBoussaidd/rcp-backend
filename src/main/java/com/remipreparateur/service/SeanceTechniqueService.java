package com.remipreparateur.service;

import com.remipreparateur.dto.SeanceTechniqueDtos.*;
import com.remipreparateur.entity.Exercice;
import com.remipreparateur.entity.SeanceTechnique;
import com.remipreparateur.entity.SeanceTechniqueExercice;
import com.remipreparateur.repository.ExerciceRepository;
import com.remipreparateur.repository.SeanceTechniqueExerciceRepository;
import com.remipreparateur.repository.SeanceTechniqueRepository;
import com.remipreparateur.repository.UtilisateurRepository;
import com.remipreparateur.security.CurrentUserProvider;
import com.remipreparateur.security.Scope;
import com.remipreparateur.security.ScopeResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SeanceTechniqueService {

    private final SeanceTechniqueRepository seanceRepo;
    private final SeanceTechniqueExerciceRepository lienRepo;
    private final ExerciceRepository exerciceRepo;
    private final UtilisateurRepository utilisateurRepo;
    private final ScopeResolver scopeResolver;
    private final CurrentUserProvider currentUser;

    public SeanceTechniqueService(SeanceTechniqueRepository seanceRepo,
                                  SeanceTechniqueExerciceRepository lienRepo,
                                  ExerciceRepository exerciceRepo,
                                  UtilisateurRepository utilisateurRepo,
                                  ScopeResolver scopeResolver,
                                  CurrentUserProvider currentUser) {
        this.seanceRepo = seanceRepo;
        this.lienRepo = lienRepo;
        this.exerciceRepo = exerciceRepo;
        this.utilisateurRepo = utilisateurRepo;
        this.scopeResolver = scopeResolver;
        this.currentUser = currentUser;
    }

    public List<SeanceTechniqueResponse> lister(LocalDate debut, LocalDate fin) {
        Scope s = scopeResolver.resolve();
        List<SeanceTechnique> seances;
        if (s.all()) {
            seances = (debut != null && fin != null)
                    ? seanceRepo.findAll().stream().filter(x -> !x.getDate().isBefore(debut) && !x.getDate().isAfter(fin)).toList()
                    : seanceRepo.findAll();
        } else if (s.none()) {
            return List.of();
        } else {
            seances = (debut != null && fin != null)
                    ? seanceRepo.findByEquipeIdInAndDateBetweenOrderByDateAsc(s.equipeIds(), debut, fin)
                    : seanceRepo.findByEquipeIdInOrderByDateDesc(s.equipeIds());
        }
        return seances.stream().map(this::toResponse).toList();
    }

    @Transactional
    public SeanceTechniqueResponse creer(SeanceTechniqueRequest req) {
        UUID equipeId = currentUser.current().getEquipeId();
        if (equipeId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Aucune equipe associee au compte");
        }
        SeanceTechnique st = new SeanceTechnique();
        st.setEquipeId(equipeId);
        st.setCreePar(currentUser.current().getId());
        st.setDate(req.date());
        st.setHeureDebut(req.heureDebut());
        st.setTitre(req.titre());
        st.setObjectif(req.objectif());
        st.setDescription(req.description());
        st.setStatut("PLANIFIEE");
        st = seanceRepo.save(st);
        remplacerExercices(st.getId(), req.exerciceIds());
        return toResponse(st);
    }

    @Transactional
    public SeanceTechniqueResponse modifier(UUID id, SeanceTechniqueRequest req) {
        SeanceTechnique st = charge(id);
        scopeResolver.verifieAcces(st.getEquipeId());
        if ("REALISEE".equals(st.getStatut())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Une seance realisee ne peut plus etre modifiee");
        }
        st.setDate(req.date());
        st.setHeureDebut(req.heureDebut());
        st.setTitre(req.titre());
        st.setObjectif(req.objectif());
        st.setDescription(req.description());
        seanceRepo.save(st);
        remplacerExercices(st.getId(), req.exerciceIds());
        return toResponse(st);
    }

    public SeanceTechniqueResponse marquerRealisee(UUID id) {
        SeanceTechnique st = charge(id);
        scopeResolver.verifieAcces(st.getEquipeId());
        st.setStatut("REALISEE");
        return toResponse(seanceRepo.save(st));
    }

    @Transactional
    public void supprimer(UUID id) {
        SeanceTechnique st = charge(id);
        scopeResolver.verifieAcces(st.getEquipeId());
        lienRepo.deleteBySeanceTechniqueId(id);
        seanceRepo.deleteById(id);
    }

    // ── Helpers ──
    private SeanceTechnique charge(UUID id) {
        return seanceRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seance technique introuvable"));
    }

    private void remplacerExercices(UUID seanceId, List<UUID> exerciceIds) {
        lienRepo.deleteBySeanceTechniqueId(seanceId);
        if (exerciceIds == null) return;
        short ordre = 0;
        for (UUID exId : exerciceIds) {
            SeanceTechniqueExercice lien = new SeanceTechniqueExercice();
            lien.setSeanceTechniqueId(seanceId);
            lien.setExerciceId(exId);
            lien.setOrdre(ordre++);
            lienRepo.save(lien);
        }
    }

    private SeanceTechniqueResponse toResponse(SeanceTechnique st) {
        List<SeanceTechniqueExercice> liens = lienRepo.findBySeanceTechniqueIdOrderByOrdreAsc(st.getId());
        Map<UUID, Exercice> exercices = exerciceRepo.findAllById(
                        liens.stream().map(SeanceTechniqueExercice::getExerciceId).toList())
                .stream().collect(Collectors.toMap(Exercice::getId, Function.identity()));

        List<ExerciceLigne> lignes = new ArrayList<>();
        int dureeTotale = 0;
        double sommePonderee = 0;
        for (SeanceTechniqueExercice l : liens) {
            Exercice e = exercices.get(l.getExerciceId());
            if (e == null) continue;
            int duree = e.getDureeMinutes() != null ? e.getDureeMinutes() : 0;
            dureeTotale += duree;
            if (e.getIntensite() != null) sommePonderee += (double) e.getIntensite() * duree;
            lignes.add(new ExerciceLigne(e.getId(), e.getNom(), e.getCategorie(),
                    e.getDureeMinutes(), e.getIntensite(), e.getObjectif(),
                    e.getDescription(), e.getSchemaJson(), l.getOrdre()));
        }
        Double intensiteMoyenne = dureeTotale > 0 ? Math.round((sommePonderee / dureeTotale) * 10) / 10.0 : null;

        String creeParNom = st.getCreePar() != null
                ? utilisateurRepo.findById(st.getCreePar())
                    .map(c -> ((c.getPrenom() != null ? c.getPrenom() + " " : "") + (c.getNom() != null ? c.getNom() : "")).trim())
                    .orElse(null)
                : null;

        return new SeanceTechniqueResponse(
                st.getId(), st.getEquipeId(), st.getDate(), st.getHeureDebut(),
                st.getTitre(), st.getObjectif(), st.getDescription(), st.getStatut(), creeParNom,
                dureeTotale, intensiteMoyenne, lignes);
    }
}
