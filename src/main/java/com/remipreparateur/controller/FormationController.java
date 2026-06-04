package com.remipreparateur.controller;

import com.remipreparateur.dto.FormationDtos.FormationRequest;
import com.remipreparateur.dto.FormationDtos.FormationResponse;
import com.remipreparateur.service.FormationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Formations tactiques personnalisées (niveau club).
 * Lecture : staff. Ecriture : entraineur (+ super-admin). Suppression : createur/president (service).
 */
@RestController
@RequestMapping("/api/formations")
public class FormationController {

    private final FormationService formationService;

    public FormationController(FormationService formationService) {
        this.formationService = formationService;
    }

    @GetMapping
    public List<FormationResponse> lister() {
        return formationService.lister();
    }

    @PostMapping
    public ResponseEntity<FormationResponse> creer(@Valid @RequestBody FormationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(formationService.creer(req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID id) {
        formationService.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
