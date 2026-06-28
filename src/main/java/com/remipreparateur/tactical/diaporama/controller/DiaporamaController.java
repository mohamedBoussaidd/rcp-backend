package com.remipreparateur.tactical.diaporama.controller;

import com.remipreparateur.tactical.diaporama.dto.DiaporamaDtos.*;
import com.remipreparateur.tactical.diaporama.service.DiaporamaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Diaporamas de séance, niveau club/équipe.
 * Lecture : {@code diaporama:read} ; écriture : {@code diaporama:write} (+ règle créateur dans le
 * service) ; modération/suppression élargie : {@code diaporama:manage} (cf. SecurityConfig & service).
 */
@RestController
@RequestMapping("/api/diaporamas")
public class DiaporamaController {

    private final DiaporamaService service;

    public DiaporamaController(DiaporamaService service) {
        this.service = service;
    }

    @GetMapping
    public List<DiaporamaResume> lister() {
        return service.lister();
    }

    @GetMapping("/{id}")
    public DiaporamaDetail detail(@PathVariable UUID id) {
        return service.detail(id);
    }

    @PostMapping
    public ResponseEntity<DiaporamaDetail> creer(@Valid @RequestBody DiaporamaCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(req));
    }

    @PutMapping("/{id}")
    public DiaporamaDetail modifier(@PathVariable UUID id, @Valid @RequestBody DiaporamaUpdateRequest req) {
        return service.modifier(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/dupliquer")
    public ResponseEntity<DiaporamaDetail> dupliquer(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.dupliquer(id));
    }

    @PostMapping("/{id}/slides")
    public ResponseEntity<SlideResponse> ajouterSlide(@PathVariable UUID id, @Valid @RequestBody SlideRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.ajouterSlide(id, req));
    }

    @PutMapping("/{id}/slides/{slideId}")
    public SlideResponse modifierSlide(@PathVariable UUID id, @PathVariable UUID slideId,
                                       @Valid @RequestBody SlideRequest req) {
        return service.modifierSlide(id, slideId, req);
    }

    @DeleteMapping("/{id}/slides/{slideId}")
    public ResponseEntity<Void> supprimerSlide(@PathVariable UUID id, @PathVariable UUID slideId) {
        service.supprimerSlide(id, slideId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/reordonner")
    public DiaporamaDetail reordonner(@PathVariable UUID id, @Valid @RequestBody ReordonnerRequest req) {
        return service.reordonner(id, req);
    }
}
