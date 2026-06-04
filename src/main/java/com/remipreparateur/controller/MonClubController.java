package com.remipreparateur.controller;

import com.remipreparateur.dto.GestionDtos.*;
import com.remipreparateur.security.CurrentUserProvider;
import com.remipreparateur.service.GestionClubService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Gestion d'un club par son president : equipes + membres. */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('PRESIDENT')")
public class MonClubController {

    private final GestionClubService gestion;
    private final CurrentUserProvider currentUser;

    public MonClubController(GestionClubService gestion, CurrentUserProvider currentUser) {
        this.gestion = gestion;
        this.currentUser = currentUser;
    }

    @GetMapping("/mon-club")
    public MonClubResponse monClub() {
        return gestion.monClub(currentUser.current());
    }

    // ── Equipes ──
    @PostMapping("/mon-club/equipes")
    public ResponseEntity<EquipeResponse> creerEquipe(@Valid @RequestBody EquipeRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(gestion.creerEquipe(currentUser.current(), req));
    }

    @PutMapping("/equipes/{id}")
    public EquipeResponse modifierEquipe(@PathVariable UUID id, @Valid @RequestBody EquipeRequest req) {
        return gestion.modifierEquipe(currentUser.current(), id, req);
    }

    @DeleteMapping("/equipes/{id}")
    public ResponseEntity<Void> supprimerEquipe(@PathVariable UUID id) {
        gestion.supprimerEquipe(currentUser.current(), id);
        return ResponseEntity.noContent().build();
    }

    // ── Membres ──
    @GetMapping("/mon-club/membres")
    public java.util.List<MembreResponse> listerMembres() {
        return gestion.monClub(currentUser.current()).membres();
    }

    @PostMapping("/mon-club/membres")
    public ResponseEntity<MembreResponse> creerMembre(@Valid @RequestBody MembreCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(gestion.creerMembre(currentUser.current(), req));
    }

    @PutMapping("/membres/{id}")
    public MembreResponse modifierMembre(@PathVariable UUID id, @RequestBody MembreUpdateRequest req) {
        return gestion.modifierMembre(currentUser.current(), id, req);
    }

    @DeleteMapping("/membres/{id}")
    public ResponseEntity<Void> supprimerMembre(@PathVariable UUID id) {
        gestion.supprimerMembre(currentUser.current(), id);
        return ResponseEntity.noContent().build();
    }
}
