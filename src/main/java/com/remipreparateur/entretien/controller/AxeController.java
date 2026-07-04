package com.remipreparateur.entretien.controller;

import com.remipreparateur.entretien.dto.EntretienDtos.AxeRequest;
import com.remipreparateur.entretien.dto.EntretienDtos.AxeResponse;
import com.remipreparateur.entretien.service.EntretienService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Axes de travail d'un joueur (staff). Lecture = {@code axe:read}, écriture = {@code axe:write}
 * (cf. SecurityConfig). Portée filtrée par équipe dans le service.
 */
@RestController
@RequestMapping("/api/axes")
public class AxeController {

    private final EntretienService service;

    public AxeController(EntretienService service) {
        this.service = service;
    }

    @GetMapping
    public List<AxeResponse> lister(@RequestParam UUID joueurId) {
        return service.listerAxes(joueurId);
    }

    @PostMapping
    public AxeResponse creer(@RequestParam UUID joueurId, @Valid @RequestBody AxeRequest req) {
        return service.creerAxe(joueurId, req);
    }

    @PutMapping("/{id}")
    public AxeResponse modifier(@PathVariable UUID id, @Valid @RequestBody AxeRequest req) {
        return service.modifierAxe(id, req);
    }

    @DeleteMapping("/{id}")
    public void supprimer(@PathVariable UUID id) {
        service.supprimerAxe(id);
    }
}
