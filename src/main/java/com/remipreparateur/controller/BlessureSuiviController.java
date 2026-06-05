package com.remipreparateur.controller;

import com.remipreparateur.dto.BlessureSuiviDtos.EtapeResponse;
import com.remipreparateur.dto.BlessureSuiviDtos.EtapeStatutRequest;
import com.remipreparateur.dto.BlessureSuiviDtos.NoteRequest;
import com.remipreparateur.dto.BlessureSuiviDtos.NoteResponse;
import com.remipreparateur.service.BlessureSuiviService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Suivi d'une blessure : journal d'évolution + protocole RTP.
 * Sous /api/blessures/** : lecture staff, écriture MEDICAL/SUPER_ADMIN (SecurityConfig).
 */
@RestController
@RequestMapping("/api/blessures/{blessureId}")
public class BlessureSuiviController {

    private final BlessureSuiviService service;

    public BlessureSuiviController(BlessureSuiviService service) {
        this.service = service;
    }

    // ── Journal ──

    @GetMapping("/notes")
    public List<NoteResponse> listerNotes(@PathVariable UUID blessureId) {
        return service.listerNotes(blessureId);
    }

    @PostMapping("/notes")
    public ResponseEntity<NoteResponse> ajouterNote(@PathVariable UUID blessureId,
                                                    @Valid @RequestBody NoteRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.ajouterNote(blessureId, req));
    }

    @DeleteMapping("/notes/{noteId}")
    public ResponseEntity<Void> supprimerNote(@PathVariable UUID blessureId, @PathVariable UUID noteId) {
        service.supprimerNote(blessureId, noteId);
        return ResponseEntity.noContent().build();
    }

    // ── Protocole RTP ──

    @GetMapping("/rtp")
    public List<EtapeResponse> listerRtp(@PathVariable UUID blessureId) {
        return service.listerRtp(blessureId);
    }

    @PostMapping("/rtp")
    public ResponseEntity<List<EtapeResponse>> initialiserRtp(@PathVariable UUID blessureId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.initialiserRtp(blessureId));
    }

    @PatchMapping("/rtp/{etapeId}")
    public EtapeResponse majEtape(@PathVariable UUID blessureId, @PathVariable UUID etapeId,
                                  @Valid @RequestBody EtapeStatutRequest req) {
        return service.majEtape(blessureId, etapeId, req.statut());
    }

    @DeleteMapping("/rtp")
    public ResponseEntity<Void> supprimerRtp(@PathVariable UUID blessureId) {
        service.supprimerRtp(blessureId);
        return ResponseEntity.noContent().build();
    }
}
