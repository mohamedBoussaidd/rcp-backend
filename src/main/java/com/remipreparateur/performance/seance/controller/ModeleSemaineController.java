package com.remipreparateur.performance.seance.controller;

import com.remipreparateur.performance.seance.dto.ModeleSemaineDtos.*;
import com.remipreparateur.performance.seance.service.ModeleSemaineService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/**
 * Modèles de semaine (gabarits hebdomadaires) d'une équipe + instanciation en séances.
 * Autorisation : seances:read (GET) / seances:write (écriture), cf. SecurityConfig.
 */
@RestController
@RequestMapping("/api/modeles-semaine")
@RequiredArgsConstructor
public class ModeleSemaineController {

    private final ModeleSemaineService service;

    @GetMapping
    public List<ModeleDto> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ModeleDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    public ModeleDto create(@RequestBody ModeleRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public ModeleDto update(@PathVariable UUID id, @RequestBody ModeleRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/dupliquer")
    public ModeleDto dupliquer(@PathVariable UUID id) {
        return service.dupliquer(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PostMapping("/{id}/instancier")
    public InstancierResult instancier(@PathVariable UUID id, @RequestBody InstancierRequest req) {
        return service.instancier(id, req);
    }
}
