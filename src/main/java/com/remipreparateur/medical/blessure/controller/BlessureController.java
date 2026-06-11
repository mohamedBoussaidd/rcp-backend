package com.remipreparateur.medical.blessure.controller;

import com.remipreparateur.medical.blessure.dto.BlessureDtos.BlessureRequest;
import com.remipreparateur.medical.blessure.dto.BlessureDtos.BlessureResponse;
import com.remipreparateur.medical.blessure.service.BlessureService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Module medical — blessures.
 * Lecture : staff (regle SecurityConfig). Ecriture : MEDICAL + SUPER_ADMIN.
 */
@RestController
@RequestMapping("/api/blessures")
public class BlessureController {

    private final BlessureService blessureService;

    public BlessureController(BlessureService blessureService) {
        this.blessureService = blessureService;
    }

    @GetMapping
    public List<BlessureResponse> lister(@RequestParam(required = false) UUID joueurId) {
        return blessureService.lister(joueurId);
    }

    @PostMapping
    public ResponseEntity<BlessureResponse> creer(@Valid @RequestBody BlessureRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(blessureService.creer(req));
    }

    @PutMapping("/{id}")
    public BlessureResponse modifier(@PathVariable UUID id, @Valid @RequestBody BlessureRequest req) {
        return blessureService.modifier(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID id) {
        blessureService.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
