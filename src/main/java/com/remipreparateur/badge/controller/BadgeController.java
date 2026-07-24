package com.remipreparateur.badge.controller;

import com.remipreparateur.badge.service.BadgeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Badges côté club : lecture du registre (tout utilisateur authentifié — le composant
 * {@code <app-badge>} en a besoin partout) et surcharge de couleur du club (6 tons).
 *   - Lecture couleurs club : tout utilisateur authentifié (le joueur PWA reçoit le thème de son club).
 *   - Écriture : président ({@code club:manage}) ou super-admin (via contexte club).
 */
@RestController
@RequestMapping("/api/badges")
public class BadgeController {

    private final BadgeService service;

    public BadgeController(BadgeService service) {
        this.service = service;
    }

    @GetMapping("/registry")
    public BadgeService.RegistryDto registry() {
        return service.registry();
    }

    @GetMapping("/couleurs-club")
    public List<BadgeService.CouleurTonDto> couleursClub() {
        return service.couleursDuClub();
    }

    @PutMapping("/couleurs-club")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('club:manage')")
    public List<BadgeService.CouleurTonDto> majCouleursClub(@RequestBody List<BadgeService.CouleurTonDto> valeurs) {
        return service.majCouleursDuClub(valeurs);
    }
}
