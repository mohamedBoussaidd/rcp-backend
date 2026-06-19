package com.remipreparateur.notification.controller;

import com.remipreparateur.notification.dto.NotifConfigDtos.ConfigDto;
import com.remipreparateur.notification.dto.NotifConfigDtos.DroitEnvoiDto;
import com.remipreparateur.notification.dto.NotifConfigDtos.DroitEnvoiRequest;
import com.remipreparateur.notification.dto.NotifConfigDtos.RoutageDto;
import com.remipreparateur.notification.service.NotifConfigService;
import com.remipreparateur.shared.security.ScopeResolver;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Configuration des notifications par équipe (tout le staff) : seuils, digests, rappels,
 * routage par rôle et droits d'émission des joueurs. Portée = équipe active du staff.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotifConfigController {

    private final NotifConfigService service;
    private final ScopeResolver scopeResolver;

    public NotifConfigController(NotifConfigService service, ScopeResolver scopeResolver) {
        this.service = service;
        this.scopeResolver = scopeResolver;
    }

    // ── Config (seuils + digests + rappels) ──
    @GetMapping("/config")
    public ConfigDto getConfig() {
        return service.getConfigDto(scopeResolver.equipeActiveUnique());
    }

    @PutMapping("/config")
    public ConfigDto updateConfig(@RequestBody ConfigDto dto) {
        return service.updateConfig(scopeResolver.equipeActiveUnique(), dto);
    }

    // ── Routage par rôle ──
    @GetMapping("/routage")
    public List<RoutageDto> getRoutage() {
        return service.listRoutages(scopeResolver.equipeActiveUnique());
    }

    @PutMapping("/routage")
    public List<RoutageDto> updateRoutage(@RequestBody List<RoutageDto> dtos) {
        return service.updateRoutages(scopeResolver.equipeActiveUnique(), dtos);
    }

    // ── Droits d'émission des joueurs ──
    @GetMapping("/droits")
    public List<DroitEnvoiDto> getDroits() {
        return service.listDroits(scopeResolver.equipeActiveUnique());
    }

    @PutMapping("/droits/{joueurId}")
    public DroitEnvoiDto setDroit(@PathVariable UUID joueurId, @RequestBody DroitEnvoiRequest req) {
        return service.setDroit(scopeResolver.equipeActiveUnique(), joueurId, req.niveau());
    }
}
