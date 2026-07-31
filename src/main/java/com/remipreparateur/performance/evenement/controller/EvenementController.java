package com.remipreparateur.performance.evenement.controller;

import com.remipreparateur.performance.evenement.dto.EvenementDtos.EvenementRequest;
import com.remipreparateur.performance.evenement.dto.EvenementDtos.EvenementResponse;
import com.remipreparateur.performance.evenement.service.EvenementService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Événements extrasportifs du calendrier. Module SOCLE « Planning » : aucune gate d'abonnement.
 * Lecture sous {@code seances:read}, écriture sous {@code seances:write} (cf. SecurityConfig).
 */
@RestController
@RequestMapping("/api/evenements")
public class EvenementController {

    private final EvenementService service;

    public EvenementController(EvenementService service) {
        this.service = service;
    }

    @GetMapping
    public List<EvenementResponse> lister(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return service.lister(debut, fin);
    }

    @PostMapping
    public EvenementResponse creer(@Valid @RequestBody EvenementRequest req) {
        return service.creer(req);
    }

    @PutMapping("/{id}")
    public EvenementResponse modifier(@PathVariable UUID id, @Valid @RequestBody EvenementRequest req) {
        return service.modifier(id, req);
    }

    @DeleteMapping("/{id}")
    public void supprimer(@PathVariable UUID id) {
        service.supprimer(id);
    }
}
