package com.remipreparateur.club.controller;

import com.remipreparateur.club.dto.GestionDtos.*;
import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.club.service.GestionClubService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Gestion d'un club : équipes + membres.
 *   - Membres (création / édition / suppression / liaison fiche) : permission {@code membres:manage}
 *     (président & entraîneur en chef = club entier ; entraîneur = sa seule équipe). Le service
 *     applique en plus la hiérarchie + le périmètre (cf. GestionClubService).
 *   - Équipes : permission {@code club:manage} (président, entraîneur en chef, super-admin).
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('membres:manage') or hasAuthority('club:manage')")
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

    // ── Equipes (président / super-admin uniquement) ──
    @PostMapping("/mon-club/equipes")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('club:manage')")
    public ResponseEntity<EquipeResponse> creerEquipe(@Valid @RequestBody EquipeRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(gestion.creerEquipe(currentUser.current(), req));
    }

    @PutMapping("/equipes/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('club:manage')")
    public EquipeResponse modifierEquipe(@PathVariable UUID id, @Valid @RequestBody EquipeRequest req) {
        return gestion.modifierEquipe(currentUser.current(), id, req);
    }

    @DeleteMapping("/equipes/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('club:manage')")
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

    // ── Liaison compte JOUEUR ↔ fiche ──
    @PutMapping("/membres/{id}/fiche")
    public MembreResponse lierFiche(@PathVariable UUID id, @Valid @RequestBody LierFicheRequest req) {
        return gestion.lierFiche(currentUser.current(), id, req.joueurId());
    }

    @DeleteMapping("/membres/{id}/fiche")
    public MembreResponse delierFiche(@PathVariable UUID id) {
        return gestion.delierFiche(currentUser.current(), id);
    }
}
