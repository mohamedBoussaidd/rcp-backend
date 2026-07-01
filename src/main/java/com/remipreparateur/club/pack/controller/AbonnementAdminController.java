package com.remipreparateur.club.pack.controller;

import com.remipreparateur.club.pack.ClubModulesService;
import com.remipreparateur.club.pack.PackDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Administration commerciale (packs & modules des clubs), réservée au SUPER_ADMIN.
 *
 * <ul>
 *   <li>Catalogue des modules et CRUD des packs (prix saisi manuellement).</li>
 *   <li>Affectation d'un pack à un club + surcharges module par module (add-on / retrait).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AbonnementAdminController {

    private final ClubModulesService service;

    public AbonnementAdminController(ClubModulesService service) {
        this.service = service;
    }

    // ── Catalogue des modules (pour la matrice de l'UI) ──
    @GetMapping("/modules")
    public List<ModuleDto> modules() {
        return service.catalogueModules();
    }

    // ── Packs ──
    @GetMapping("/packs")
    public List<PackDto> packs() {
        return service.listerPacks();
    }

    @PostMapping("/packs")
    public ResponseEntity<PackDto> creerPack(@RequestBody PackUpsertRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creerPack(req));
    }

    @PutMapping("/packs/{code}")
    public PackDto majPack(@PathVariable String code, @RequestBody PackUpsertRequest req) {
        return service.majPack(code, req);
    }

    @DeleteMapping("/packs/{code}")
    public ResponseEntity<Void> supprimerPack(@PathVariable String code) {
        service.supprimerPack(code);
        return ResponseEntity.noContent().build();
    }

    // ── Abonnement d'un club ──
    @GetMapping("/clubs/{clubId}/abonnement")
    public ClubAbonnementDto abonnement(@PathVariable UUID clubId) {
        return service.abonnement(clubId);
    }

    @PutMapping("/clubs/{clubId}/pack")
    public ClubAbonnementDto assignerPack(@PathVariable UUID clubId, @RequestBody AssignerPackRequest req) {
        return service.assignerPack(clubId, req.packCode());
    }

    @PutMapping("/clubs/{clubId}/modules/{moduleCode}")
    public ClubAbonnementDto definirModule(@PathVariable UUID clubId, @PathVariable String moduleCode,
                                           @RequestBody DefinirModuleRequest req) {
        return service.definirModule(clubId, moduleCode, req.actif());
    }
}
