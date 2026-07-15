package com.remipreparateur.medical.blessure.controller;

import com.remipreparateur.medical.blessure.dto.BlessureSuiviDtos.EtapeCreateRequest;
import com.remipreparateur.medical.blessure.dto.BlessureSuiviDtos.EtapeResponse;
import com.remipreparateur.medical.blessure.dto.BlessureSuiviDtos.EtapeUpdateRequest;
import com.remipreparateur.medical.blessure.dto.BlessureSuiviDtos.NoteRequest;
import com.remipreparateur.medical.blessure.dto.BlessureSuiviDtos.NoteResponse;
import com.remipreparateur.medical.blessure.dto.BlessureSuiviDtos.OrdreRequest;
import com.remipreparateur.medical.blessure.service.BlessureSuiviService;
import com.remipreparateur.medical.protocole.dto.ProtocoleModeleDtos.DepuisBlessureRequest;
import com.remipreparateur.medical.protocole.dto.ProtocoleModeleDtos.ModeleResponse;
import com.remipreparateur.medical.protocole.service.ProtocoleModeleService;
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
    private final ProtocoleModeleService protocoleService;

    public BlessureSuiviController(BlessureSuiviService service, ProtocoleModeleService protocoleService) {
        this.service = service;
        this.protocoleService = protocoleService;
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

    /** Modèle suggéré selon type/zone/gravité de la blessure ; 204 si aucun modèle éligible. */
    @GetMapping("/rtp/suggestion")
    public ResponseEntity<ModeleResponse> suggestion(@PathVariable UUID blessureId) {
        ModeleResponse m = service.suggestion(blessureId);
        return m == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(m);
    }

    /** Initialise le protocole en clonant le modèle demandé (sans modeleId : le modèle suggéré). */
    @PostMapping("/rtp")
    public ResponseEntity<List<EtapeResponse>> initialiserRtp(@PathVariable UUID blessureId,
                                                              @RequestParam(required = false) UUID modeleId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.initialiserRtp(blessureId, modeleId));
    }

    @PostMapping("/rtp/etapes")
    public ResponseEntity<EtapeResponse> ajouterEtape(@PathVariable UUID blessureId,
                                                      @Valid @RequestBody EtapeCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.ajouterEtape(blessureId, req));
    }

    @PatchMapping("/rtp/{etapeId}")
    public EtapeResponse modifierEtape(@PathVariable UUID blessureId, @PathVariable UUID etapeId,
                                       @Valid @RequestBody EtapeUpdateRequest req) {
        return service.modifierEtape(blessureId, etapeId, req);
    }

    @DeleteMapping("/rtp/{etapeId}")
    public ResponseEntity<Void> supprimerEtape(@PathVariable UUID blessureId, @PathVariable UUID etapeId) {
        service.supprimerEtape(blessureId, etapeId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/rtp/ordre")
    public List<EtapeResponse> reordonner(@PathVariable UUID blessureId, @Valid @RequestBody OrdreRequest req) {
        return service.reordonner(blessureId, req.etapeIds());
    }

    /** Capitalise le protocole en cours en nouveau modèle du club (critères pré-remplis). */
    @PostMapping("/rtp/enregistrer-modele")
    public ResponseEntity<ModeleResponse> enregistrerModele(@PathVariable UUID blessureId,
                                                            @Valid @RequestBody DepuisBlessureRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(protocoleService.enregistrerDepuisBlessure(blessureId, req));
    }

    @DeleteMapping("/rtp")
    public ResponseEntity<Void> supprimerRtp(@PathVariable UUID blessureId) {
        service.supprimerRtp(blessureId);
        return ResponseEntity.noContent().build();
    }
}
