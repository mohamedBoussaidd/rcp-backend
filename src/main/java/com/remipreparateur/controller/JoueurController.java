package com.remipreparateur.controller;

import com.remipreparateur.dto.GpsHistoriqueDto;
import com.remipreparateur.dto.VitesseJoueurDto;
import com.remipreparateur.entity.Joueur;
import com.remipreparateur.security.ScopeResolver;
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
    private final ScopeResolver scopeResolver;

    @GetMapping
    public List<Joueur> getAll() {
        return joueurService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Joueur> getById(@PathVariable UUID id) {
        return joueurService.findById(id)
                .map(j -> { scopeResolver.verifieAcces(j.getEquipeId()); return ResponseEntity.ok(j); })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Joueur create(@Valid @RequestBody Joueur joueur) {
        return joueurService.create(joueur);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Joueur> update(@PathVariable UUID id, @Valid @RequestBody Joueur joueur) {
        return joueurService.findById(id).map(existing -> {
            scopeResolver.verifieAcces(existing.getEquipeId());
            joueur.setId(id);
            joueur.setEquipeId(existing.getEquipeId()); // on ne change pas l'equipe via update
            return ResponseEntity.ok(joueurService.save(joueur));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/tous")
    public List<Joueur> getTous() {
        return joueurService.findAllPlayers();
    }

    /** Fiche vitesse (vmax/vmoy en km/h) des joueurs de l'équipe, pour animer les schémas. */
    @GetMapping("/vitesses")
    public List<VitesseJoueurDto> getVitesses() {
        return joueurService.getVitesses();
    }

    @GetMapping("/{id}/gps")
    public ResponseEntity<List<GpsHistoriqueDto>> getHistoriqueGps(@PathVariable UUID id) {
        return joueurService.findById(id).map(j -> {
            scopeResolver.verifieAcces(j.getEquipeId());
            return ResponseEntity.ok(joueurService.getHistoriqueGps(id));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        return joueurService.findById(id).map(j -> {
            scopeResolver.verifieAcces(j.getEquipeId());
            joueurService.deleteById(id);
            return ResponseEntity.noContent().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }
}
