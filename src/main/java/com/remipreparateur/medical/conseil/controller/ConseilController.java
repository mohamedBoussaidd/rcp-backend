package com.remipreparateur.medical.conseil.controller;

import com.remipreparateur.medical.conseil.dto.ConseilDtos.ConseilRequest;
import com.remipreparateur.medical.conseil.dto.ConseilDtos.ConseilResponse;
import com.remipreparateur.medical.conseil.service.ConseilService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Conseils du staff au joueur — gestion par le staff (médical / préparateur).
 * Lecture = staff ; écriture = médical/préparateur (règle SecurityConfig).
 * Portée filtrée par équipe dans le service.
 */
@RestController
@RequestMapping("/api/conseils")
public class ConseilController {

    private final ConseilService conseilService;

    public ConseilController(ConseilService conseilService) {
        this.conseilService = conseilService;
    }

    /** Conseils d'équipe ; avec {@code joueurId} : + les conseils personnels du joueur. */
    @GetMapping
    public List<ConseilResponse> lister(@RequestParam(required = false) UUID joueurId) {
        return conseilService.listerPourStaff(joueurId);
    }

    @PostMapping
    public ConseilResponse creer(@Valid @RequestBody ConseilRequest req) {
        return conseilService.creer(req);
    }

    @PutMapping("/{id}")
    public ConseilResponse modifier(@PathVariable UUID id, @Valid @RequestBody ConseilRequest req) {
        return conseilService.modifier(id, req);
    }

    @DeleteMapping("/{id}")
    public void supprimer(@PathVariable UUID id) {
        conseilService.supprimer(id);
    }
}
