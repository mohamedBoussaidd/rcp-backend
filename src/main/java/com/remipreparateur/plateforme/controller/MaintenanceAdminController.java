package com.remipreparateur.plateforme.controller;

import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.notification.entity.Priorite;
import com.remipreparateur.plateforme.service.BroadcastService;
import com.remipreparateur.plateforme.service.MaintenanceService;
import com.remipreparateur.plateforme.service.MaintenanceService.TacheVue;
import com.remipreparateur.plateforme.service.ParametrePlateformeService;
import com.remipreparateur.shared.security.CurrentUserProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Console d'exploitation plateforme (SUPER_ADMIN) : tâches de maintenance déclenchables à la
 * main, réglage de la rétention des notifications, et diffusion d'annonces (broadcast).
 */
@RestController
@RequestMapping("/api/admin/maintenance")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class MaintenanceAdminController {

    private final MaintenanceService maintenanceService;
    private final BroadcastService broadcastService;
    private final ParametrePlateformeService parametres;
    private final CurrentUserProvider currentUser;

    public MaintenanceAdminController(MaintenanceService maintenanceService,
                                      BroadcastService broadcastService,
                                      ParametrePlateformeService parametres,
                                      CurrentUserProvider currentUser) {
        this.maintenanceService = maintenanceService;
        this.broadcastService = broadcastService;
        this.parametres = parametres;
        this.currentUser = currentUser;
    }

    // ── Tâches planifiées / manuelles ──

    @GetMapping("/taches")
    public List<TacheVue> taches() {
        return maintenanceService.lister();
    }

    @PostMapping("/taches/{code}/executer")
    public TacheVue executer(@PathVariable String code) {
        return maintenanceService.executer(code, currentUser.current().getId());
    }

    // ── Rétention des notifications ──

    @GetMapping("/retention")
    public RetentionDto retention() {
        return new RetentionDto(
                parametres.getInt(ParametrePlateformeService.CLE_RETENTION_LUES, 30),
                parametres.getInt(ParametrePlateformeService.CLE_RETENTION_NON_LUES, 90));
    }

    @PutMapping("/retention")
    public RetentionDto majRetention(@RequestBody RetentionDto d) {
        int lues = Math.max(1, d.lues());
        int nonLues = Math.max(1, d.nonLues());
        parametres.set(ParametrePlateformeService.CLE_RETENTION_LUES, BigDecimal.valueOf(lues));
        parametres.set(ParametrePlateformeService.CLE_RETENTION_NON_LUES, BigDecimal.valueOf(nonLues));
        return new RetentionDto(lues, nonLues);
    }

    // ── Broadcast (annonce) ──

    @PostMapping("/broadcast")
    public Map<String, Integer> broadcast(@RequestBody BroadcastRequest req) {
        if (req.titre() == null || req.titre().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Titre requis");
        }
        BroadcastService.Cible cible = parseCible(req.cible());
        Role role = null;
        if (cible == BroadcastService.Cible.ROLE) {
            role = parseRole(req.role());
            if (role == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rôle requis pour une cible par rôle");
        }
        UUID clubId = cible == BroadcastService.Cible.CLUB ? req.clubId() : null;
        if (cible == BroadcastService.Cible.CLUB && clubId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Club requis pour une cible club");
        }
        Priorite priorite = "URGENTE".equalsIgnoreCase(req.priorite()) ? Priorite.URGENTE : Priorite.NORMALE;
        int n = broadcastService.diffuser(cible, clubId, role, req.titre().trim(),
                req.corps(), req.lien(), priorite, currentUser.current().getId());
        return Map.of("destinataires", n);
    }

    private static BroadcastService.Cible parseCible(String s) {
        try { return BroadcastService.Cible.valueOf(String.valueOf(s).toUpperCase()); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cible invalide"); }
    }

    private static Role parseRole(String s) {
        try { return s == null ? null : Role.valueOf(s.toUpperCase()); }
        catch (Exception e) { return null; }
    }

    // ── DTOs ──

    public record RetentionDto(int lues, int nonLues) {}

    public record BroadcastRequest(String cible, UUID clubId, String role, String titre,
                                   String corps, String lien, String priorite) {}
}
