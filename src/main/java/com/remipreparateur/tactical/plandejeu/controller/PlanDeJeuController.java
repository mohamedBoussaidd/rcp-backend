package com.remipreparateur.tactical.plandejeu.controller;

import com.remipreparateur.tactical.plandejeu.dto.PlanDeJeuDtos.PlanDeJeuResponse;
import com.remipreparateur.tactical.plandejeu.dto.PlanDeJeuDtos.ReordonnerRequest;
import com.remipreparateur.tactical.plandejeu.dto.PlanDeJeuDtos.SectionCreateRequest;
import com.remipreparateur.tactical.plandejeu.dto.PlanDeJeuDtos.SectionResponse;
import com.remipreparateur.tactical.plandejeu.dto.PlanDeJeuDtos.SectionUpdateRequest;
import com.remipreparateur.tactical.plandejeu.service.PlanDeJeuService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Plan de jeu (« document d'identité équipe »), niveau équipe.
 * Lecture : staff. Écriture : entraineur / president / super-admin.
 * Le document de l'équipe active est créé à la volée au premier GET.
 */
@RestController
@RequestMapping("/api/plan-de-jeu")
public class PlanDeJeuController {

    private final PlanDeJeuService planService;

    public PlanDeJeuController(PlanDeJeuService planService) {
        this.planService = planService;
    }

    @GetMapping
    public PlanDeJeuResponse get() {
        return planService.getPlan();
    }

    @PostMapping("/sections")
    public ResponseEntity<SectionResponse> ajouterSection(@Valid @RequestBody SectionCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planService.ajouterSection(req));
    }

    @PutMapping("/sections/{id}")
    public SectionResponse modifierSection(@PathVariable UUID id, @Valid @RequestBody SectionUpdateRequest req) {
        return planService.modifierSection(id, req);
    }

    @DeleteMapping("/sections/{id}")
    public ResponseEntity<Void> supprimerSection(@PathVariable UUID id) {
        planService.supprimerSection(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/reordonner")
    public PlanDeJeuResponse reordonner(@Valid @RequestBody ReordonnerRequest req) {
        return planService.reordonner(req);
    }
}
