package com.remipreparateur.tactical.seancetechnique.controller;

import com.remipreparateur.tactical.seancetechnique.dto.SeanceTechniqueDtos.SeanceTechniqueRequest;
import com.remipreparateur.tactical.seancetechnique.dto.SeanceTechniqueDtos.SeanceTechniqueResponse;
import com.remipreparateur.tactical.seancetechnique.service.SeanceTechniqueService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Seances techniques (calendrier commun, niveau equipe).
 * Lecture : staff. Ecriture : entraineur + super-admin (scope equipe, edition si non realisee).
 */
@RestController
@RequestMapping("/api/seances-techniques")
public class SeanceTechniqueController {

    private final SeanceTechniqueService service;

    public SeanceTechniqueController(SeanceTechniqueService service) {
        this.service = service;
    }

    @GetMapping
    public List<SeanceTechniqueResponse> lister(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return service.lister(debut, fin);
    }

    @PostMapping
    public ResponseEntity<SeanceTechniqueResponse> creer(@Valid @RequestBody SeanceTechniqueRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(req));
    }

    @PutMapping("/{id}")
    public SeanceTechniqueResponse modifier(@PathVariable UUID id, @Valid @RequestBody SeanceTechniqueRequest req) {
        return service.modifier(id, req);
    }

    @PatchMapping("/{id}/realiser")
    public SeanceTechniqueResponse realiser(@PathVariable UUID id) {
        return service.marquerRealisee(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
