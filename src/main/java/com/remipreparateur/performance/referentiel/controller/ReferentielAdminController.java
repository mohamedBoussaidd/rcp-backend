package com.remipreparateur.performance.referentiel.controller;

import com.remipreparateur.performance.referentiel.dto.ReferentielDtos.*;
import com.remipreparateur.performance.referentiel.service.ReferentielObjectifService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Catalogue des référentiels de la PLATEFORME (super-admin).
 *
 * <p>Même famille que les schémas globaux et les rôles globaux : le super-admin publie, les clubs
 * consomment. La règle qui structure tout : <b>on ne corrige jamais un référentiel publié</b>, on
 * en ouvre une nouvelle version, et les clubs migrent quand ils le décident.
 *
 * <p>Réservé au SUPER_ADMIN par {@code @PreAuthorize}, comme les autres consoles {@code /api/admin}
 * (badges, rôles globaux, IA) : ces routes n'apparaissent pas dans SecurityConfig, qui ne porte
 * que les règles par permission.
 */
@RestController
@RequestMapping("/api/admin/referentiels")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ReferentielAdminController {

    private final ReferentielObjectifService service;

    public ReferentielAdminController(ReferentielObjectifService service) {
        this.service = service;
    }

    @GetMapping
    public List<ReferentielResume> lister() {
        return service.listerPlateforme();
    }

    @GetMapping("/{id}")
    public ReferentielDetail detail(@PathVariable UUID id) {
        return service.detail(id);
    }

    @PostMapping
    public ReferentielDetail creer(@RequestBody ReferentielRequest req) {
        return service.creerPlateforme(req);
    }

    /** Ouvre une nouvelle version d'un référentiel publié (copie en brouillon). */
    @PostMapping("/{id}/versions")
    public ReferentielDetail nouvelleVersion(@PathVariable UUID id) {
        return service.nouvelleVersion(id);
    }

    @PutMapping("/{id}/valeurs")
    public ReferentielDetail enregistrerValeurs(@PathVariable UUID id,
                                                @RequestBody List<ValeurDto> valeurs) {
        return service.enregistrerValeurs(id, valeurs);
    }

    /** Publie un brouillon ; s'il a un parent, celui-ci passe en archive. */
    @PostMapping("/{id}/publier")
    public ReferentielResume publier(@PathVariable UUID id) {
        return service.publier(id);
    }

    @GetMapping("/ecart")
    public EcartResponse ecart(@RequestParam UUID avant, @RequestParam UUID apres) {
        return service.ecart(avant, apres);
    }

    /** Combien de clubs sont épinglés sur chaque référentiel du catalogue. */
    @GetMapping("/usage")
    public List<UsageDto> usage() {
        return service.usage();
    }

    /** Qui exactement — à consulter avant d'archiver une version. */
    @GetMapping("/{id}/clubs")
    public List<ClubUtilisateurDto> clubsUtilisateurs(@PathVariable UUID id) {
        return service.clubsUtilisateurs(id);
    }
}
