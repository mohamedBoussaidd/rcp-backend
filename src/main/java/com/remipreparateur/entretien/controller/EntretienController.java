package com.remipreparateur.entretien.controller;

import com.remipreparateur.entretien.dto.EntretienDtos.*;
import com.remipreparateur.entretien.service.EntretienService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Entretiens individuels (staff). Lecture = {@code entretien:read}, écriture = {@code entretien:write},
 * suppression = auteur (write) ou modérateur ({@code entretien:manage}). Portée par équipe (service).
 */
@RestController
@RequestMapping("/api/entretiens")
public class EntretienController {

    private final EntretienService service;

    public EntretienController(EntretienService service) {
        this.service = service;
    }

    @GetMapping
    public List<EntretienResponse> lister(
            @RequestParam UUID joueurId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return service.listerEntretiens(joueurId, type, debut, fin);
    }

    /** Synthèse de progression d'un joueur (axes actifs, séries de notes, dernière auto-éval). */
    @GetMapping("/synthese")
    public SyntheseResponse synthese(@RequestParam UUID joueurId) {
        return service.synthese(joueurId);
    }

    /** Vue équipe : dernier entretien et cadences par joueur de l'effectif de la saison active. */
    @GetMapping("/equipe")
    public List<EquipeLigne> vueEquipe() {
        return service.vueEquipe();
    }

    @PostMapping
    public EntretienResponse creer(@Valid @RequestBody EntretienRequest req) {
        return service.creerEntretien(req);
    }

    @PutMapping("/{id}")
    public EntretienResponse modifier(@PathVariable UUID id, @Valid @RequestBody EntretienRequest req) {
        return service.modifierEntretien(id, req);
    }

    @DeleteMapping("/{id}")
    public void supprimer(@PathVariable UUID id) {
        service.supprimerEntretien(id);
    }

    /** Bascule STAFF ↔ PARTAGE_JOUEUR ; réponse = état + si une notification a été émise. */
    @PatchMapping("/{id}/visibilite")
    public VisibiliteResponse basculerVisibilite(@PathVariable UUID id) {
        return service.basculerVisibilite(id);
    }
}
