package com.remipreparateur.tactical.regles.controller;

import com.remipreparateur.tactical.regles.dto.RegleTactiqueDtos.RegleTactiqueRequest;
import com.remipreparateur.tactical.regles.dto.RegleTactiqueDtos.RegleTactiqueResponse;
import com.remipreparateur.tactical.regles.dto.RegleTactiqueDtos.RegleTactiqueResume;
import com.remipreparateur.tactical.regles.service.RegleTactiqueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Jeux de règles du moteur tactique (équipe active). Lecture = regles_tactiques:read,
 * écriture = regles_tactiques:write (cf. SecurityConfig) ; module `moteur_tactique`.
 */
@RestController
@RequestMapping("/api/regles-tactiques")
@RequiredArgsConstructor
public class RegleTactiqueController {

    private final RegleTactiqueService service;

    @GetMapping
    public List<RegleTactiqueResume> lister(@RequestParam(required = false) String type,
                                            @RequestParam(required = false) String systeme) {
        return service.lister(type, systeme);
    }

    @GetMapping("/{id}")
    public RegleTactiqueResponse detail(@PathVariable UUID id) {
        return service.detail(id);
    }

    @PostMapping
    public RegleTactiqueResponse creer(@Valid @RequestBody RegleTactiqueRequest req) {
        return service.creer(req);
    }

    @PutMapping("/{id}")
    public RegleTactiqueResponse modifier(@PathVariable UUID id, @Valid @RequestBody RegleTactiqueRequest req) {
        return service.modifier(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
