package com.remipreparateur.badge.controller;

import com.remipreparateur.badge.service.BadgeAdminService;
import com.remipreparateur.badge.service.BadgeAdminService.BadgeUpdateDto;
import com.remipreparateur.badge.service.BadgeAdminService.PaletteTonDto;
import com.remipreparateur.badge.service.BadgeAdminService.TagCreateDto;
import com.remipreparateur.badge.service.BadgeService.BadgeDto;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Gestion des badges par le super-admin : édition des badges (label/icône/ton/couleur, activation)
 * et de la palette des 6 tons. La CRUD des tags plateforme s'ajoutera en P3.
 */
@RestController
@RequestMapping("/api/admin/badges")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class BadgeAdminController {

    private final BadgeAdminService service;

    public BadgeAdminController(BadgeAdminService service) {
        this.service = service;
    }

    @GetMapping
    public List<BadgeDto> lister() {
        return service.listerBadges();
    }

    @PostMapping
    public BadgeDto creerTag(@RequestBody TagCreateDto d) {
        return service.creerTag(d);
    }

    @PutMapping("/{cle}")
    public BadgeDto maj(@PathVariable String cle, @RequestBody BadgeUpdateDto d) {
        return service.majBadge(cle, d);
    }

    @DeleteMapping("/{cle}")
    public void supprimerTag(@PathVariable String cle) {
        service.supprimerTag(cle);
    }

    @GetMapping("/palette")
    public List<PaletteTonDto> palette() {
        return service.palette();
    }

    @PutMapping("/palette")
    public List<PaletteTonDto> majPalette(@RequestBody List<PaletteTonDto> valeurs) {
        return service.majPalette(valeurs);
    }
}
