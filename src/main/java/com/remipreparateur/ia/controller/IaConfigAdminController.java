package com.remipreparateur.ia.controller;

import com.remipreparateur.ia.service.IaConfigAdminService;
import com.remipreparateur.ia.service.IaConfigAdminService.ClubIaDto;
import com.remipreparateur.ia.service.IaConfigAdminService.ConfigRequest;
import com.remipreparateur.ia.service.IaConfigAdminService.FeatureDto;
import com.remipreparateur.ia.service.IaConfigAdminService.QuotaFeatureDto;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Console IA super-admin : config (provider + clé + modèle) par club et quotas par feature.
 * Les clés API ne sont jamais renvoyées en clair (masquées).
 */
@RestController
@RequestMapping("/api/admin/ia")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class IaConfigAdminController {

    private final IaConfigAdminService service;

    public IaConfigAdminController(IaConfigAdminService service) {
        this.service = service;
    }

    @GetMapping("/clubs")
    public List<ClubIaDto> clubs() {
        return service.listerClubs();
    }

    @PutMapping("/clubs/{clubId}")
    public ClubIaDto configurer(@PathVariable UUID clubId, @RequestBody ConfigRequest req) {
        return service.configurer(clubId, req);
    }

    @DeleteMapping("/clubs/{clubId}")
    public void revoquer(@PathVariable UUID clubId) {
        service.revoquer(clubId);
    }

    /** Catalogue des features IA (drive les onglets Prompts & Quotas de l'écran d'admin). */
    @GetMapping("/features")
    public List<FeatureDto> features() {
        return service.features();
    }

    // ── Quotas unifiés : défaut global + surcharge par club, pour toutes les features ──

    @GetMapping("/quotas")
    public List<QuotaFeatureDto> quotas() {
        return service.quotas();
    }

    public record ValeurRequest(Integer valeur) {}   // null = retirer la surcharge (côté club)

    @PutMapping("/quotas/defaut/{feature}")
    public List<QuotaFeatureDto> majDefaut(@PathVariable String feature, @RequestBody ValeurRequest req) {
        return service.majDefaut(feature, req.valeur());
    }

    @PutMapping("/quotas/club/{clubId}/{feature}")
    public List<QuotaFeatureDto> majClub(@PathVariable UUID clubId, @PathVariable String feature,
                                         @RequestBody ValeurRequest req) {
        return service.majClub(clubId, feature, req.valeur());
    }
}
