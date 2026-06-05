package com.remipreparateur.controller;

import com.remipreparateur.dto.WellnessDtos.WellnessResponse;
import com.remipreparateur.service.WellnessService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Ressenti quotidien (wellness) — consultation staff.
 * Lecture : staff (regle SecurityConfig) ; portee filtree par equipe dans le service.
 */
@RestController
@RequestMapping("/api/wellness")
public class WellnessController {

    private final WellnessService wellnessService;

    public WellnessController(WellnessService wellnessService) {
        this.wellnessService = wellnessService;
    }

    @GetMapping
    public List<WellnessResponse> lister(@RequestParam(required = false) UUID joueurId) {
        return wellnessService.listerPourStaff(joueurId);
    }

    /** Marque la gêne d'une saisie comme traitée (médical / préparateur). */
    @PatchMapping("/{id}/gene-traitee")
    public WellnessResponse traiterGene(@PathVariable UUID id) {
        return wellnessService.traiterGene(id);
    }
}
