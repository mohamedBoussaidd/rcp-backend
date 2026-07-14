package com.remipreparateur.club.controller;

import com.remipreparateur.club.entity.Equipe;
import com.remipreparateur.shared.security.Scope;
import com.remipreparateur.shared.security.ScopeResolver;
import com.remipreparateur.club.repository.EquipeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Contexte de navigation : expose au front les équipes du périmètre AUTORISÉ de l'utilisateur
 * (identité seule, sans réduction par le contexte courant) — alimente le sélecteur d'équipe du
 * staff multi-équipes. Tout rôle authentifié ; un super-admin hors contexte reçoit une liste vide.
 */
@RestController
@RequestMapping("/api/contexte")
@RequiredArgsConstructor
public class ContexteController {

    private final ScopeResolver scopeResolver;
    private final EquipeRepository equipeRepository;

    public record EquipeContexteDto(UUID id, String nom) {}

    @GetMapping("/equipes")
    public List<EquipeContexteDto> equipesAccessibles() {
        Scope s = scopeResolver.scopeAutorise();
        if (s.all() || s.none()) {
            return List.of();
        }
        return equipeRepository.findAllById(s.equipeIds()).stream()
                .sorted(Comparator.comparing(Equipe::getNom, String.CASE_INSENSITIVE_ORDER))
                .map(e -> new EquipeContexteDto(e.getId(), e.getNom()))
                .toList();
    }
}
