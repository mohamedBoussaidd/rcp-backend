package com.remipreparateur.performance.seance.controller;

import com.remipreparateur.performance.seance.dto.SeanceDtos.RoleBlocDto;
import com.remipreparateur.performance.seance.repository.ReferentielDominanteRepository;
import com.remipreparateur.performance.seance.repository.ReferentielRoleBlocRepository;
import com.remipreparateur.performance.seance.repository.ReferentielSousPrincipeRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Référentiels du mode avancé des séances (globaux, seedés en V61) : dominantes
 * (SEANCE / ATHLETIQUE) et sous-principes du projet de jeu par phase. Lecture seule,
 * tout utilisateur authentifié (le front n'affiche les sélecteurs que si le module
 * seance_avancee est actif — mais la fiche séance, elle, est lisible par tous).
 */
@RestController
@RequestMapping("/api/referentiels")
public class ReferentielSeanceController {

    private final ReferentielDominanteRepository dominantes;
    private final ReferentielSousPrincipeRepository sousPrincipes;
    private final ReferentielRoleBlocRepository rolesBloc;

    public ReferentielSeanceController(ReferentielDominanteRepository dominantes,
                                       ReferentielSousPrincipeRepository sousPrincipes,
                                       ReferentielRoleBlocRepository rolesBloc) {
        this.dominantes = dominantes;
        this.sousPrincipes = sousPrincipes;
        this.rolesBloc = rolesBloc;
    }

    public record DominanteDto(UUID id, String code, String libelle, String famille, short ordre) {}
    public record SousPrincipeDto(UUID id, String code, String libelle, String phase, short ordre) {}

    /** @param rolesBloc rôles du staff sur un bloc (V66) : ▶ mène, ⚖ arbitre, ⚽ ballons… */
    public record ReferentielsSeanceDto(List<DominanteDto> dominantes,
                                        List<SousPrincipeDto> sousPrincipes,
                                        List<RoleBlocDto> rolesBloc) {}

    @GetMapping("/seance-avancee")
    public ReferentielsSeanceDto seanceAvancee() {
        return new ReferentielsSeanceDto(
                dominantes.findAllByOrderByFamilleAscOrdreAsc().stream()
                        .map(d -> new DominanteDto(d.getId(), d.getCode(), d.getLibelle(), d.getFamille(), d.getOrdre()))
                        .toList(),
                sousPrincipes.findAllByOrderByPhaseAscOrdreAsc().stream()
                        .map(s -> new SousPrincipeDto(s.getId(), s.getCode(), s.getLibelle(), s.getPhase(), s.getOrdre()))
                        .toList(),
                rolesBloc.findAllByOrderByOrdreAsc().stream()
                        .map(r -> new RoleBlocDto(r.getCode(), r.getLibelle(), r.getIcone()))
                        .toList());
    }
}
