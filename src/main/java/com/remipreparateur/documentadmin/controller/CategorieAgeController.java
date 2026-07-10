package com.remipreparateur.documentadmin.controller;

import com.remipreparateur.documentadmin.dto.DocumentAdminDtos.CategorieAgeRequest;
import com.remipreparateur.documentadmin.dto.DocumentAdminDtos.CategorieAgeResponse;
import com.remipreparateur.documentadmin.service.CategorieAgeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Catégories d'âge du club (Paramètres). Lecture = {@code configuration:read}, écriture =
 * {@code configuration:write} (cf. SecurityConfig) — même droits que le reste de Paramètres,
 * pas de permission dédiée : concept réutilisable au-delà du seul module documents.
 */
@RestController
@RequestMapping("/api/categories-age")
public class CategorieAgeController {

    private final CategorieAgeService service;

    public CategorieAgeController(CategorieAgeService service) {
        this.service = service;
    }

    @GetMapping
    public List<CategorieAgeResponse> lister() {
        return service.lister();
    }

    @PostMapping
    public CategorieAgeResponse creer(@Valid @RequestBody CategorieAgeRequest req) {
        return service.creer(req);
    }

    @PutMapping("/{id}")
    public CategorieAgeResponse modifier(@PathVariable UUID id, @Valid @RequestBody CategorieAgeRequest req) {
        return service.modifier(id, req);
    }
}
