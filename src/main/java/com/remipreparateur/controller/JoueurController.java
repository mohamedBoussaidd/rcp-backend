package com.remipreparateur.controller;

import com.remipreparateur.dto.GpsHistoriqueDto;
import com.remipreparateur.entity.Joueur;
import com.remipreparateur.service.JoueurService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/joueurs")
@RequiredArgsConstructor
public class JoueurController {

    private final JoueurService joueurService;

    @GetMapping
    public List<Joueur> getAll() {
        return joueurService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Joueur> getById(@PathVariable UUID id) {
        return joueurService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Joueur create(@Valid @RequestBody Joueur joueur) {
        return joueurService.save(joueur);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Joueur> update(@PathVariable UUID id, @Valid @RequestBody Joueur joueur) {
        return joueurService.findById(id).map(existing -> {
            joueur.setId(id);
            return ResponseEntity.ok(joueurService.save(joueur));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/tous")
    public List<Joueur> getTous() {
        return joueurService.findAllPlayers();
    }

    @GetMapping("/{id}/gps")
    public ResponseEntity<List<GpsHistoriqueDto>> getHistoriqueGps(@PathVariable UUID id) {
        if (joueurService.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(joueurService.getHistoriqueGps(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        if (joueurService.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        joueurService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
