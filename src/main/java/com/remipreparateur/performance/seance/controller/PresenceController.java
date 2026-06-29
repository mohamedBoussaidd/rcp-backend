package com.remipreparateur.performance.seance.controller;

import com.remipreparateur.performance.seance.dto.PresenceDtos.*;
import com.remipreparateur.performance.seance.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/seances")
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceService presenceService;

    /** Résumés chiffrés de l'appel de plusieurs séances (dashboard, pastille « X/Y dispo »). */
    @GetMapping("/presence/resumes")
    public ResponseEntity<List<ResumeAppel>> resumes(@RequestParam("ids") List<UUID> ids) {
        return ResponseEntity.ok(presenceService.resumes(ids));
    }

    /** Feuille de présence complète d'une séance (effectif + statuts). */
    @GetMapping("/{id}/presence")
    public ResponseEntity<FeuillePresence> getFeuille(@PathVariable UUID id) {
        return ResponseEntity.ok(presenceService.getFeuille(id));
    }

    /** Sauvegarde groupée de toute la feuille. */
    @PutMapping("/{id}/presence")
    public ResponseEntity<FeuillePresence> saveFeuille(
            @PathVariable UUID id,
            @RequestBody SaveFeuillePresence req) {
        return ResponseEntity.ok(presenceService.saveFeuille(id, req));
    }

    /** Mise à jour de la présence d'un seul joueur. */
    @PutMapping("/{id}/presence/{joueurId}")
    public ResponseEntity<LignePresence> saveUne(
            @PathVariable UUID id,
            @PathVariable UUID joueurId,
            @RequestBody SavePresence req) {
        return ResponseEntity.ok(presenceService.saveUne(id, joueurId, req));
    }
}
