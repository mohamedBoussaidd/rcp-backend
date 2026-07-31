package com.remipreparateur.performance.rpe.controller;

import com.remipreparateur.performance.rpe.dto.RpeDtos.RpeResponse;
import com.remipreparateur.performance.rpe.service.RpeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * RPE de séance (charge subjective) — consultation staff.
 * Lecture : staff (regle SecurityConfig) ; portee filtree par equipe dans le service.
 */
@RestController
@RequestMapping("/api/rpe")
public class RpeController {

    private final RpeService rpeService;

    public RpeController(RpeService rpeService) {
        this.rpeService = rpeService;
    }

    @GetMapping
    public List<RpeResponse> lister(@RequestParam(required = false) UUID joueurId) {
        return rpeService.listerPourStaff(joueurId);
    }

    /**
     * Marque la gêne d'un RPE de séance comme traitée (médical / préparateur).
     * Pendant de {@code PATCH /api/wellness/{id}/gene-traitee} : depuis V91 une gêne peut
     * être déclarée après une séance, elle doit donc pouvoir être soldée de la même façon.
     * {@code resolution} = ARCHIVEE (défaut) ou CONVERTIE (convertie en blessure).
     */
    @PatchMapping("/{id}/gene-traitee")
    public RpeResponse traiterGene(@PathVariable UUID id,
                                   @RequestParam(required = false) String resolution) {
        return rpeService.traiterGene(id, resolution);
    }

    /** Rouvre une gêne traitée (médical) : elle redevient active dans les alertes. */
    @PatchMapping("/{id}/gene-rouvrir")
    public RpeResponse rouvrirGene(@PathVariable UUID id) {
        return rpeService.rouvrirGene(id);
    }
}
