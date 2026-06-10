package com.remipreparateur.controller;

import com.remipreparateur.dto.MatchDtos.*;
import com.remipreparateur.service.MatchService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Module Match (cycle de vie avant/après), niveau équipe.
 * Lecture : staff. Écriture : entraineur / president / super-admin.
 * Les matchs sont rattachés à l'équipe active (cf. ScopeResolver.equipeActiveUnique()).
 */
@RestController
@RequestMapping("/api/matchs")
public class MatchController {

    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    // ── Liste / création / détail ──

    @GetMapping
    public List<MatchResume> lister() {
        return matchService.lister();
    }

    @PostMapping
    public ResponseEntity<MatchResponse> creer(@Valid @RequestBody MatchCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(matchService.creer(req));
    }

    @GetMapping("/{id}")
    public MatchResponse detail(@PathVariable UUID id) {
        return matchService.detail(id);
    }

    @PutMapping("/{id}/infos")
    public MatchResponse modifierInfos(@PathVariable UUID id, @Valid @RequestBody MatchInfosRequest req) {
        return matchService.modifierInfos(id, req);
    }

    @PutMapping("/{id}/debrief")
    public MatchResponse modifierDebrief(@PathVariable UUID id, @Valid @RequestBody MatchDebriefRequest req) {
        return matchService.modifierDebrief(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID id) {
        matchService.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    // ── Schémas adverses ──

    @PostMapping("/{id}/schemas")
    public ResponseEntity<SchemaResponse> ajouterSchema(@PathVariable UUID id, @Valid @RequestBody SchemaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(matchService.ajouterSchema(id, req));
    }

    @PutMapping("/schemas/{schemaId}")
    public SchemaResponse modifierSchema(@PathVariable UUID schemaId, @Valid @RequestBody SchemaRequest req) {
        return matchService.modifierSchema(schemaId, req);
    }

    @DeleteMapping("/schemas/{schemaId}")
    public ResponseEntity<Void> supprimerSchema(@PathVariable UUID schemaId) {
        matchService.supprimerSchema(schemaId);
        return ResponseEntity.noContent().build();
    }

    // ── Compo ──

    @PutMapping("/{id}/compo")
    public MatchResponse enregistrerCompo(@PathVariable UUID id, @Valid @RequestBody CompoUpdateRequest req) {
        return matchService.enregistrerCompo(id, req);
    }

    // ── Joueurs à surveiller ──

    @PostMapping("/{id}/surveilles")
    public MatchResponse ajouterSurveille(@PathVariable UUID id, @RequestBody SurveilleRequest req) {
        return matchService.ajouterSurveille(id, req);
    }

    @DeleteMapping("/surveilles/{surveilleId}")
    public MatchResponse supprimerSurveille(@PathVariable UUID surveilleId) {
        return matchService.supprimerSurveille(surveilleId);
    }

    // ── Session GPS ──

    @GetMapping("/sessions-gps")
    public List<SessionGpsOption> sessionsDisponibles() {
        return matchService.sessionsDisponibles();
    }

    @PutMapping("/{id}/session-gps")
    public MatchResponse definirSessionGps(@PathVariable UUID id, @RequestBody SessionGpsRequest req) {
        return matchService.definirSessionGps(id, req);
    }

    @GetMapping("/{id}/charge-gps")
    public List<ChargeJoueur> chargeGps(@PathVariable UUID id) {
        return matchService.chargeGps(id);
    }

    // ── Statistiques ──

    @GetMapping("/stats-compo")
    public List<JoueurCompoStats> statsCompo() {
        return matchService.statsCompo();
    }

    /** Joueurs actuellement blessés (à placer automatiquement au repos côté UI). */
    @GetMapping("/blesses")
    public List<UUID> joueursBlesses() {
        return matchService.joueursBlesses();
    }
}
