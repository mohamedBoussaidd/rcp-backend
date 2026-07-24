package com.remipreparateur.tactical.schema.controller;

import com.remipreparateur.tactical.schema.dto.SchemaTactiqueDtos.SchemaRechercheResponse;
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

    /** Bibliothèque GLOBALE (super-admin) — schémas fournis à tous les clubs. */
    @GetMapping("/globaux")
    public List<SchemaTactiqueResponse> listerGlobaux() {
        return schemaService.listerGlobaux();
    }

    /** Crée un schéma global (super-admin uniquement, vérifié dans le service). */
    @PostMapping("/globaux")
    public ResponseEntity<SchemaTactiqueResponse> creerGlobal(@Valid @RequestBody SchemaTactiqueRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(schemaService.creerGlobal(req));
    }

    /** Recherche cross-club (super-admin) : schémas des clubs, filtrés nom/catégorie. */
    @GetMapping("/recherche")
    public List<SchemaRechercheResponse> rechercher(@RequestParam(required = false) List<UUID> clubIds,
                                                    @RequestParam(required = false) String q,
                                                    @RequestParam(required = false) String categorie) {
        return schemaService.rechercher(clubIds, q, categorie);
    }

    /** Promeut un schéma de club en schéma global (copie ; l'original n'est jamais touché). */
    @PostMapping("/{id}/promouvoir")
    public ResponseEntity<SchemaTactiqueResponse> promouvoir(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(schemaService.promouvoir(id));
    }

    @PostMapping
    public ResponseEntity<SchemaTactiqueResponse> creer(@Valid @RequestBody SchemaTactiqueRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(schemaService.creer(req));
    }

    @PutMapping("/{id}")
    public SchemaTactiqueResponse modifier(@PathVariable UUID id, @Valid @RequestBody SchemaTactiqueRequest req) {
        return schemaService.modifier(id, req);
    }

    /** Duplique un schéma existant en une copie éditable attribuée à l'utilisateur courant. */
    @PostMapping("/{id}/dupliquer")
    public ResponseEntity<SchemaTactiqueResponse> dupliquer(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(schemaService.dupliquer(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID id) {
        schemaService.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
