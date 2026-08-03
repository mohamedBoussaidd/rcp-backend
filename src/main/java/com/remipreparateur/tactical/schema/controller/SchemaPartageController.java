package com.remipreparateur.tactical.schema.controller;

import com.remipreparateur.tactical.schema.dto.SchemaPartageDtos.PartageRequest;
import com.remipreparateur.tactical.schema.dto.SchemaPartageDtos.PartageResponse;
import com.remipreparateur.tactical.schema.service.SchemaPartageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Partage de schémas aux joueurs — côté STAFF (V100).
 * Gardé par la permission {@code schemas:partager} (cf. SecurityConfig), elle-même liée au
 * module {@code schemas_joueur}. Le joueur, lui, lit ses schémas via {@code /api/moi/schemas}.
 */
@RestController
@RequestMapping("/api/schemas/partages")
public class SchemaPartageController {

    private final SchemaPartageService service;

    public SchemaPartageController(SchemaPartageService service) {
        this.service = service;
    }

    /** Historique des partages ; filtrable sur un schéma précis. */
    @GetMapping
    public List<PartageResponse> lister(@RequestParam(required = false) UUID schemaId) {
        return service.lister(schemaId);
    }

    @PostMapping
    public ResponseEntity<List<PartageResponse>> partager(@Valid @RequestBody PartageRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.partager(req));
    }

    /** Retire un partage : le schéma disparaît de l'espace des joueurs concernés. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> retirer(@PathVariable UUID id) {
        service.retirer(id);
        return ResponseEntity.noContent().build();
    }
}
