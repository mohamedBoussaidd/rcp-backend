package com.remipreparateur.medical.wellness.controller;

import com.remipreparateur.medical.wellness.dto.WellnessDtos.WellnessResponse;
import com.remipreparateur.medical.wellness.service.WellnessService;
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

    /**
     * Marque la gêne d'une saisie comme traitée (médical / préparateur).
     * {@code resolution} = ARCHIVEE (défaut) ou CONVERTIE (convertie en blessure).
     */
    @PatchMapping("/{id}/gene-traitee")
    public WellnessResponse traiterGene(@PathVariable UUID id,
                                        @RequestParam(required = false) String resolution) {
        return wellnessService.traiterGene(id, resolution);
    }

    /** Rouvre une gêne traitée (médical) : elle redevient active dans les alertes. */
    @PatchMapping("/{id}/gene-rouvrir")
    public WellnessResponse rouvrirGene(@PathVariable UUID id) {
        return wellnessService.rouvrirGene(id);
    }
}
