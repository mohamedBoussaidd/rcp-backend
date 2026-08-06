package com.remipreparateur.performance.referentiel.controller;

import com.remipreparateur.performance.referentiel.dto.ReferentielDtos.*;
import com.remipreparateur.performance.referentiel.service.ReferentielObjectifService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Référentiels de charge, côté CLUB : consulter le catalogue, adopter, personnaliser.
 *
 * <p>Le club lit les référentiels de la plateforme et ses propres copies ; il ne modifie jamais
 * un référentiel plateforme (il le duplique). La gestion du catalogue lui-même est ailleurs
 * ({@link ReferentielAdminController}, super-admin).
 */
@RestController
@RequestMapping("/api/referentiels")
public class ReferentielController {

    private final ReferentielObjectifService service;

    public ReferentielController(ReferentielObjectifService service) {
        this.service = service;
    }

    /** Vocabulaire (métriques, postes, contextes) + catalogue adoptable, en un appel. */
    @GetMapping
    public CatalogueResponse catalogue() {
        return service.catalogue();
    }

    @GetMapping("/{id}")
    public ReferentielDetail detail(@PathVariable UUID id) {
        return service.detail(id);
    }

    /** Écart case par case entre deux référentiels (copie vs standard, ou aperçu de migration). */
    @GetMapping("/ecart")
    public EcartResponse ecart(@RequestParam UUID avant, @RequestParam UUID apres) {
        return service.ecart(avant, apres);
    }

    /** Copie un référentiel du catalogue chez le club, pour l'adapter. */
    @PostMapping("/dupliquer")
    public ReferentielDetail dupliquer(@RequestBody DuplicationRequest req) {
        return service.dupliquerPourClub(req);
    }

    /** Modifie les valeurs d'un référentiel DU CLUB (un référentiel plateforme est immuable). */
    @PutMapping("/{id}/valeurs")
    public ReferentielDetail enregistrerValeurs(@PathVariable UUID id,
                                                @RequestBody List<ValeurDto> valeurs) {
        return service.enregistrerValeurs(id, valeurs);
    }

    // ── Adoption ──

    @GetMapping("/adoptions")
    public List<AdoptionDto> adoptions() {
        return service.adoptions();
    }

    /** Adopte un référentiel pour tout le club, ou pour une équipe précise. */
    @PostMapping("/adoptions")
    public AdoptionDto adopter(@RequestBody AdoptionRequest req) {
        return service.adopter(req);
    }

    @DeleteMapping("/adoptions/{id}")
    public void retirerAdoption(@PathVariable UUID id) {
        service.retirerAdoption(id);
    }

    /** Le référentiel réellement appliqué à une équipe (surcharge équipe → défaut club → aucun). */
    @GetMapping("/resolution")
    public ResolutionDto resolution(@RequestParam(required = false) UUID equipeId) {
        return service.resolution(equipeId);
    }
}
