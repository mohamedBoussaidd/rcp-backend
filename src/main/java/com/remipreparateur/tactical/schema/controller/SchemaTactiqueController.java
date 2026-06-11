package com.remipreparateur.tactical.schema.controller;

import com.remipreparateur.tactical.schema.dto.SchemaTactiqueDtos.SchemaTactiqueRequest;
import com.remipreparateur.tactical.schema.dto.SchemaTactiqueDtos.SchemaTactiqueResponse;
import com.remipreparateur.tactical.schema.service.SchemaTactiqueService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Bibliothèque de schémas tactiques (niveau club).
 * Lecture : staff. Écriture : entraineur (+ super-admin).
 * Édition/suppression : createur, ou president/super-admin (verifie dans le service).
 */
@RestController
@RequestMapping("/api/schemas")
public class SchemaTactiqueController {

    private final SchemaTactiqueService schemaService;

    public SchemaTactiqueController(SchemaTactiqueService schemaService) {
        this.schemaService = schemaService;
    }

    @GetMapping
    public List<SchemaTactiqueResponse> lister() {
        return schemaService.lister();
    }

    @PostMapping
    public ResponseEntity<SchemaTactiqueResponse> creer(@Valid @RequestBody SchemaTactiqueRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(schemaService.creer(req));
    }

    @PutMapping("/{id}")
    public SchemaTactiqueResponse modifier(@PathVariable UUID id, @Valid @RequestBody SchemaTactiqueRequest req) {
        return schemaService.modifier(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID id) {
        schemaService.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
