package com.remipreparateur.ia.controller;

import com.remipreparateur.ia.service.IaConfigAdminService;
import com.remipreparateur.ia.service.IaConfigAdminService.ClubIaDto;
import com.remipreparateur.ia.service.IaConfigAdminService.ConfigRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
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

    @GetMapping("/quotas")
    public Map<String, Integer> quotas() {
        return service.quotasDefauts();
    }

    @PutMapping("/quotas")
    public Map<String, Integer> majQuotas(@RequestBody Map<String, Integer> valeurs) {
        return service.majQuotasDefauts(valeurs);
    }
}
