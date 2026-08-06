package com.remipreparateur.performance.objectifperiode.controller;

import com.remipreparateur.performance.objectifperiode.dto.ArbitrageDtos.ArbitrageRequest;
import com.remipreparateur.performance.objectifperiode.dto.ArbitrageDtos.SemaineArbitrageDto;
import com.remipreparateur.performance.objectifperiode.dto.ObjectifPeriodeDtos.*;
import com.remipreparateur.performance.objectifperiode.service.ArbitrageSemaineService;
import com.remipreparateur.performance.objectifperiode.service.ModeleObjectifService;
import com.remipreparateur.performance.objectifperiode.service.ObjectifPeriodeService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Modèles d'objectif du club et objectifs accrochés aux périodes de la saison.
 *
 * <p>Droits résolus par SecurityConfig ({@code objectifs:read} en lecture, {@code objectifs:write}
 * en écriture) ; le module {@code objectifs_performance} est vérifié dans les services.
 */
@RestController
@RequestMapping("/api/objectifs")
public class ObjectifPeriodeController {

    private final ModeleObjectifService modeleService;
    private final ObjectifPeriodeService objectifService;
    private final ArbitrageSemaineService arbitrageService;

    public ObjectifPeriodeController(ModeleObjectifService modeleService,
                                     ObjectifPeriodeService objectifService,
                                     ArbitrageSemaineService arbitrageService) {
        this.modeleService = modeleService;
        this.objectifService = objectifService;
        this.arbitrageService = arbitrageService;
    }

    // ── Modèles réutilisables ──

    @GetMapping("/modeles")
    public List<ModeleResume> listerModeles(@RequestParam(required = false) String typePeriode) {
        return modeleService.lister(typePeriode);
    }

    @GetMapping("/modeles/{id}")
    public ModeleDetail detailModele(@PathVariable UUID id) {
        return modeleService.detail(id);
    }

    @PostMapping("/modeles")
    public ModeleDetail creerModele(@RequestBody ModeleRequest req) {
        return modeleService.enregistrer(null, req);
    }

    @PutMapping("/modeles/{id}")
    public ModeleDetail modifierModele(@PathVariable UUID id, @RequestBody ModeleRequest req) {
        return modeleService.enregistrer(id, req);
    }

    @DeleteMapping("/modeles/{id}")
    public void supprimerModele(@PathVariable UUID id) {
        modeleService.supprimer(id);
    }

    // ── Objectifs de période ──

    /** État de toutes les périodes d'une équipe : « objectifs définis » ou « à définir ». */
    @GetMapping("/periodes")
    public List<EtatPeriodeDto> etatPeriodes(@RequestParam UUID saisonId, @RequestParam UUID equipeId) {
        return objectifService.etatPeriodes(saisonId, equipeId);
    }

    @GetMapping("/periodes/{periodeId}")
    public ObjectifPeriodeDetail detail(@PathVariable UUID periodeId) {
        return objectifService.detailParPeriode(periodeId);
    }

    /**
     * Aperçu sans écriture : montre la répartition en phases et l'avertissement éventuel
     * (« 3 semaines pour 4 phases, le Pic sera supprimé ») avant que le préparateur valide.
     */
    @PostMapping("/periodes/apercu")
    public ApercuResponse apercu(@RequestBody InstancierRequest req) {
        return objectifService.apercu(req);
    }

    /** Instancie ou ré-instancie un modèle. Écrase les retouches manuelles — prévenir avant. */
    @PostMapping("/periodes/instancier")
    public ObjectifPeriodeDetail instancier(@RequestBody InstancierRequest req) {
        return objectifService.instancier(req);
    }

    @PutMapping("/periodes/{periodeId}/valeurs")
    public ObjectifPeriodeDetail enregistrerValeurs(@PathVariable UUID periodeId,
                                                    @RequestBody List<ValeurPeriodeDto> valeurs) {
        return objectifService.enregistrerValeurs(periodeId, valeurs);
    }

    @DeleteMapping("/periodes/{periodeId}")
    public void supprimer(@PathVariable UUID periodeId) {
        objectifService.supprimer(periodeId);
    }

    // ── Semaine à deux matchs ──
    // L'équipe n'est jamais dans l'URL : elle vient du contexte actif, comme pour l'objectif
    // hebdo (409 si le contexte couvre plusieurs équipes — on n'arbitre pas en lot).

    @GetMapping("/arbitrage-semaine")
    public SemaineArbitrageDto etatArbitrage(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return arbitrageService.etat(date);
    }

    @PutMapping("/arbitrage-semaine")
    public SemaineArbitrageDto arbitrer(@RequestBody ArbitrageRequest req) {
        return arbitrageService.enregistrer(req);
    }

    @DeleteMapping("/arbitrage-semaine")
    public SemaineArbitrageDto annulerArbitrage(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return arbitrageService.supprimer(date);
    }
}
