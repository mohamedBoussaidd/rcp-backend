package com.remipreparateur.performance.seance.controller;

import com.remipreparateur.performance.seance.dto.TypeSeanceDtos.CiblesRequest;
import com.remipreparateur.performance.seance.dto.TypeSeanceDtos.TypeSeanceResponse;
import com.remipreparateur.performance.seance.entity.TypeSeance;
import com.remipreparateur.performance.seance.entity.TypeSeanceCible;
import com.remipreparateur.performance.seance.repository.TypeSeanceCibleRepository;
import com.remipreparateur.performance.seance.repository.TypeSeanceRepository;
import com.remipreparateur.shared.security.ScopeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/type-seances")
@RequiredArgsConstructor
public class TypeSeanceController {

    private final TypeSeanceRepository typeSeanceRepository;
    private final TypeSeanceCibleRepository cibleRepository;
    private final ScopeResolver scopeResolver;

    /** Catalogue des types enrichi des cibles du club actif. */
    @GetMapping
    public List<TypeSeanceResponse> getAll() {
        UUID club = scopeResolver.clubActif();
        Map<UUID, TypeSeanceCible> cibles = cibleRepository.findByClubId(club).stream()
                .collect(Collectors.toMap(TypeSeanceCible::getTypeSeanceId, Function.identity()));
        return typeSeanceRepository.findAll().stream()
                .map(t -> toResponse(t, cibles.get(t.getId())))
                .toList();
    }

    /** Paramètre les cibles d'un type pour le club actif (upsert). */
    @PutMapping("/{id}/cibles")
    public TypeSeanceResponse setCibles(@PathVariable UUID id, @RequestBody CiblesRequest req) {
        TypeSeance type = typeSeanceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Type de séance introuvable"));
        UUID club = scopeResolver.clubActif();
        TypeSeanceCible cible = cibleRepository.findByClubIdAndTypeSeanceId(club, id)
                .orElseGet(() -> {
                    TypeSeanceCible c = new TypeSeanceCible();
                    c.setClubId(club);
                    c.setTypeSeanceId(id);
                    return c;
                });
        cible.setObjectifDistanceM(req.objectifDistanceM());
        cible.setObjectifDistanceHauteIntensiteM(req.objectifDistanceHauteIntensiteM());
        cible.setObjectifIntensite(req.objectifIntensite());
        return toResponse(type, cibleRepository.save(cible));
    }

    private TypeSeanceResponse toResponse(TypeSeance t, TypeSeanceCible c) {
        return new TypeSeanceResponse(
                t.getId(), t.getCode(), t.getLibelle(), t.getJourSemaine(),
                t.getIntensiteTheorique(), t.getObjectifPrincipal(), t.getDureeTheoriqueMin(),
                c == null ? null : c.getObjectifDistanceM(),
                c == null ? null : c.getObjectifDistanceHauteIntensiteM(),
                c == null ? null : c.getObjectifIntensite());
    }
}
