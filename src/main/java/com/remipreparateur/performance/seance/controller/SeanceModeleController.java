package com.remipreparateur.performance.seance.controller;

import com.remipreparateur.performance.seance.dto.SeanceDtos.ExercicesRequest;
import com.remipreparateur.performance.seance.dto.SeanceModeleDtos.*;
import com.remipreparateur.performance.seance.service.SeanceModeleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Bibliothèque de séances-modèles (espace Coaching, niveau club). Gardée par {@code coaching:access}
 * dans SecurityConfig. Édition/suppression restreintes au créateur (vérifié dans le service).
 */
@RestController
@RequestMapping("/api/seances-modeles")
public class SeanceModeleController {

    private final SeanceModeleService service;

    public SeanceModeleController(SeanceModeleService service) {
        this.service = service;
    }

    @GetMapping
    public List<SeanceModeleResponse> lister() {
        return service.lister();
    }

    @GetMapping("/{id}")
    public SeanceModeleDetail detail(@PathVariable UUID id) {
        return service.detail(id);
    }

    @PostMapping
    public ResponseEntity<SeanceModeleResponse> creer(@Valid @RequestBody SeanceModeleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(req));
    }

    @PutMapping("/{id}")
    public SeanceModeleResponse modifier(@PathVariable UUID id, @Valid @RequestBody SeanceModeleRequest req) {
        return service.modifier(id, req);
    }

    @PutMapping("/{id}/exercices")
    public SeanceModeleDetail remplacerExercices(@PathVariable UUID id, @RequestBody ExercicesRequest req) {
        return service.remplacerExercices(id, req);
    }

    @PostMapping("/{id}/dupliquer")
    public ResponseEntity<SeanceModeleResponse> dupliquer(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.dupliquer(id));
    }

    @PostMapping("/{id}/planifier")
    public ResponseEntity<PlanifieResponse> planifier(@PathVariable UUID id, @Valid @RequestBody PlanifierRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.planifier(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
