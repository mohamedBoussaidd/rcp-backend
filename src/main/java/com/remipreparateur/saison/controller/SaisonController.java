package com.remipreparateur.saison.controller;

import com.remipreparateur.saison.dto.SaisonDtos.*;
import com.remipreparateur.saison.service.SaisonService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Saisons d'une équipe : cycle de vie (ouvrir/clôturer), périodes typées et effectif.
 * Autorisation : saison:read (GET) / saison:manage (écriture), cf. SecurityConfig.
 */
@RestController
@RequestMapping("/api/saisons")
@RequiredArgsConstructor
public class SaisonController {

    private final SaisonService service;

    @GetMapping
    public List<SaisonDto> getAll() {
        return service.findAll();
    }

    /**
     * Saison EN_COURS de l'équipe active + période courante (bandeau dashboard). null si aucune.
     * La période est calculée contre l'{@link com.remipreparateur.shared.time.Horloge} : un SUPER_ADMIN
     * peut voyager dans la saison via l'en-tête {@code X-Date-Simulee} (honoré uniquement pour lui).
     */
    @GetMapping("/courante")
    public SaisonDto courante() {
        return service.courante();
    }

    @GetMapping("/{id}")
    public SaisonDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    public SaisonDto ouvrir(@RequestBody SaisonRequest req) {
        return service.ouvrir(req);
    }

    @PutMapping("/{id}")
    public SaisonDto update(@PathVariable UUID id, @RequestBody SaisonRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/cloturer")
    public SaisonDto cloturer(@PathVariable UUID id) {
        return service.cloturer(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    // ── Périodes ──
    @PostMapping("/{id}/periodes/defaut")
    public SaisonDto genererPeriodes(@PathVariable UUID id) {
        return service.genererPeriodesParDefaut(id);
    }

    @PutMapping("/{id}/periodes")
    public SaisonDto remplacerPeriodes(@PathVariable UUID id, @RequestBody PeriodesRequest req) {
        return service.remplacerPeriodes(id, req);
    }

    // ── Effectif ──
    @GetMapping("/{id}/effectif")
    public List<EffectifMembreDto> effectif(@PathVariable UUID id) {
        return service.effectif(id);
    }

    @PutMapping("/{id}/effectif")
    public List<EffectifMembreDto> definirEffectif(@PathVariable UUID id, @RequestBody EffectifRequest req) {
        return service.definirEffectif(id, req);
    }

    @GetMapping("/{id}/reconduction")
    public ReconductionProposition reconduction(@PathVariable UUID id) {
        return service.propositionReconduction(id);
    }

    /** Bilan synthétique d'une saison (comparaison inter-saisons). */
    @GetMapping("/{id}/bilan")
    public BilanSaison bilan(@PathVariable UUID id) {
        return service.bilan(id);
    }

    @PostMapping("/{id}/reconduction")
    public ReconductionResultat appliquerReconduction(@PathVariable UUID id, @RequestBody EffectifRequest req) {
        return service.appliquerReconduction(id, req);
    }
}
