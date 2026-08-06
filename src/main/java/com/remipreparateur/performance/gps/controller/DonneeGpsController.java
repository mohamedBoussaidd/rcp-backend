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

    @GetMapping("/charge-cible/{joueurId}")
    public Object getChargeCible(@PathVariable UUID joueurId) {
        verifieAccesJoueur(joueurId);
        return predictionService.getChargeCible(joueurId);
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

    /**
     * Onglet « Objectif de charge » de la fiche joueur : les 3 courbes de la période.
     * Gardé par {@code predictions:read} comme le reste de la fiche — c'est le module
     * (résolu côté service) qui décide si la trajectoire existe, pas une permission de plus.
     */
    @GetMapping("/joueur/{joueurId}/objectif-trajectoire")
    public Object getObjectifTrajectoire(@PathVariable UUID joueurId,
                                         @RequestParam(required = false) UUID periodeId) {
        verifieAccesJoueur(joueurId);
        return predictionService.getObjectifTrajectoireJoueur(joueurId, periodeId);
    }

    /** Bilan d'une période : prescrit contre réalisé. Portée résolue côté service. */
    @GetMapping("/equipe/bilan-periode")
    public Object getBilanPeriode(@RequestParam UUID periodeId) {
        return predictionService.getBilanPeriode(periodeId);
    }

    /** 404 si le joueur ciblé est hors du périmètre (équipe) de l'utilisateur — anti-IDOR. */
    private void verifieAccesJoueur(UUID joueurId) {
        var j = joueurService.findById(joueurId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Joueur introuvable"));
        scopeResolver.verifieAccesPersonne(j.getId(), j.getClubId());   // Phase 4 : dérivé de l'effectif
    }
}
