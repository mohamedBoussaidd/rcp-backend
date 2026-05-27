package com.remipreparateur.controller;

import com.remipreparateur.service.PredictionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/predictions")
@RequiredArgsConstructor
public class DonneeGpsController {

    private final PredictionService predictionService;

    @GetMapping("/risque/{joueurId}")
    public Object getRisque(@PathVariable UUID joueurId) {
        return predictionService.getRisqueBlessure(joueurId);
    }

    @GetMapping("/fatigue/{joueurId}")
    public Object getFatigue(@PathVariable UUID joueurId) {
        return predictionService.getFatigue(joueurId);
    }

    @GetMapping("/equipe")
    public Object getEquipe() {
        return predictionService.getResumeEquipe();
    }

    @GetMapping("/charge-collective")
    public Object getChargeCollective() {
        return predictionService.getChargeCollective();
    }

    @GetMapping("/seance/{seanceId}/rapport")
    public Object getRapportSeance(@PathVariable UUID seanceId) {
        return predictionService.getRapportSeance(seanceId);
    }
}
