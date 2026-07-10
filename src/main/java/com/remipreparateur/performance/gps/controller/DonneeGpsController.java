package com.remipreparateur.performance.gps.controller;

import com.remipreparateur.performance.analytics.service.PredictionService;
import com.remipreparateur.joueur.service.JoueurService;
import com.remipreparateur.performance.seance.service.SeanceService;
import com.remipreparateur.shared.security.ScopeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@RestController
@RequestMapping("/api/predictions")
@RequiredArgsConstructor
public class DonneeGpsController {

    private final PredictionService predictionService;
    private final JoueurService joueurService;
    private final SeanceService seanceService;
    private final ScopeResolver scopeResolver;

    @GetMapping("/risque/{joueurId}")
    public Object getRisque(@PathVariable UUID joueurId) {
        verifieAccesJoueur(joueurId);
        return predictionService.getRisqueBlessure(joueurId);
    }

    @GetMapping("/fatigue/{joueurId}")
    public Object getFatigue(@PathVariable UUID joueurId) {
        verifieAccesJoueur(joueurId);
        return predictionService.getFatigue(joueurId);
    }

    @GetMapping("/equipe")
    public Object getEquipe() {
        return predictionService.getResumeEquipe();
    }

    @GetMapping("/charge-collective")
    public Object getChargeCollective(@RequestParam(defaultValue = "4") int semaines) {
        return predictionService.getChargeCollective(semaines);
    }

    @GetMapping("/seance/{seanceId}/rapport")
    public Object getRapportSeance(@PathVariable UUID seanceId) {
        scopeResolver.verifieAcces(
                seanceService.findById(seanceId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Séance introuvable"))
                        .getEquipeId());
        return predictionService.getRapportSeance(seanceId);
    }

    @GetMapping("/equipe/charge")
    public Object getChargeEquipe(@RequestParam(required = false) String debut,
                                  @RequestParam(required = false) String fin,
                                  @RequestParam(required = false) String types) {
        return predictionService.getChargeEquipe(debut, fin, types);
    }

    /** 404 si le joueur ciblé est hors du périmètre (équipe) de l'utilisateur — anti-IDOR. */
    private void verifieAccesJoueur(UUID joueurId) {
        var j = joueurService.findById(joueurId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Joueur introuvable"));
        scopeResolver.verifieAccesPersonne(j.getId(), j.getClubId());   // Phase 4 : dérivé de l'effectif
    }
}
