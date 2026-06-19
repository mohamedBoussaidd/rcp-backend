package com.remipreparateur.notification.controller;

import com.remipreparateur.notification.dto.NotifConfigDtos.PreferenceDto;
import com.remipreparateur.notification.dto.NotifConfigDtos.PreferenceMeRequest;
import com.remipreparateur.notification.dto.NotifConfigDtos.PreferenceStaffRequest;
import com.remipreparateur.notification.service.NotifPreferenceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Préférences de notification : {@code /me} pour le destinataire courant (staff ou joueur),
 * {@code /joueur/{id}} pour la gestion par le staff (coupure ciblée + verrou de modification).
 */
@RestController
@RequestMapping("/api/notifications/preferences")
public class NotifPreferenceController {

    private final NotifPreferenceService service;

    public NotifPreferenceController(NotifPreferenceService service) {
        this.service = service;
    }

    // ── Mes préférences ──
    @GetMapping("/me")
    public List<PreferenceDto> mesPreferences() {
        return service.listMine();
    }

    @PutMapping("/me")
    public void majMaPreference(@RequestBody PreferenceMeRequest req) {
        service.updateMine(req.type(), req.actif());
    }

    // ── Préférences d'un joueur ciblé (staff) ──
    @GetMapping("/joueur/{joueurId}")
    public List<PreferenceDto> preferencesJoueur(@PathVariable UUID joueurId) {
        return service.listForJoueur(joueurId);
    }

    @PutMapping("/joueur/{joueurId}")
    public void majPreferenceJoueur(@PathVariable UUID joueurId, @RequestBody PreferenceStaffRequest req) {
        service.updateForJoueur(joueurId, req.type(), req.actif(), req.verrouilleParStaff());
    }

    // ── Matrice de l'équipe (staff) : tous les joueurs × types ──
    @GetMapping("/equipe")
    public com.remipreparateur.notification.dto.NotifConfigDtos.EquipeMatriceDto matrice() {
        return service.matriceEquipe();
    }

    @PutMapping("/equipe/type")
    public void setType(@RequestBody com.remipreparateur.notification.dto.NotifConfigDtos.BulkTypeRequest req) {
        service.setPourTypeEquipe(req.type(), req.actif());
    }
}
