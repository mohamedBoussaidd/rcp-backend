package com.remipreparateur.badge.controller;

import com.remipreparateur.badge.entity.TypeEntite;
import com.remipreparateur.badge.service.BadgeService.BadgeDto;
import com.remipreparateur.badge.service.EntiteBadgeService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tags posés sur des entités (exercice / séance / joueur).
 *   - Lecture : tout utilisateur authentifié (affichage des badges sur les cards).
 *   - Assignation : super-admin uniquement (les clubs ne taguent pas).
 */
@RestController
@RequestMapping("/api/badges/entite")
public class EntiteBadgeController {

    private final EntiteBadgeService service;

    public EntiteBadgeController(EntiteBadgeService service) {
        this.service = service;
    }

    /** Tags d'une entité précise. */
    @GetMapping("/{type}/{id}")
    public List<BadgeDto> badges(@PathVariable String type, @PathVariable UUID id) {
        return service.badgesDe(parseType(type), id);
    }

    /** Tous les tags d'un type, groupés par entité (affichage en liste : 1 requête). */
    @GetMapping("/{type}")
    public Map<UUID, List<BadgeDto>> parType(@PathVariable String type) {
        return service.badgesParType(parseType(type));
    }

    /** Remplace les tags d'une entité (super-admin). Corps = liste d'ids de badges. */
    @PutMapping("/{type}/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<BadgeDto> assigner(@PathVariable String type, @PathVariable UUID id,
                                   @RequestBody List<UUID> badgeIds) {
        return service.assigner(parseType(type), id, badgeIds);
    }

    private static TypeEntite parseType(String s) {
        try {
            return TypeEntite.valueOf(String.valueOf(s).toUpperCase());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type d'entité invalide : " + s);
        }
    }
}
