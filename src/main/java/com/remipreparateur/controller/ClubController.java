package com.remipreparateur.controller;

import com.remipreparateur.dto.ClubDtos.ClubCreateRequest;
import com.remipreparateur.dto.ClubDtos.ClubResponse;
import com.remipreparateur.dto.ClubDtos.ClubUpdateRequest;
import com.remipreparateur.dto.ClubDtos.EquipeApercu;
import com.remipreparateur.service.ClubService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Gestion des clubs — reserve au super-admin. */
@RestController
@RequestMapping("/api/clubs")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ClubController {

    private final ClubService clubService;

    public ClubController(ClubService clubService) {
        this.clubService = clubService;
    }

    @GetMapping
    public List<ClubResponse> lister() {
        return clubService.listerClubs();
    }

    @PostMapping
    public ResponseEntity<ClubResponse> creer(@Valid @RequestBody ClubCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clubService.creerClubAvecPresident(req));
    }

    @PutMapping("/{id}")
    public ClubResponse modifier(@PathVariable UUID id, @Valid @RequestBody ClubUpdateRequest req) {
        return clubService.modifier(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID id) {
        clubService.supprimerClub(id);
        return ResponseEntity.noContent().build();
    }

    /** Active ou archive un club. */
    @PatchMapping("/{id}/actif")
    public ClubResponse definirActif(@PathVariable UUID id, @RequestParam boolean actif) {
        return clubService.definirActif(id, actif);
    }

    /** Équipes d'un club (pour entrer dans son contexte). */
    @GetMapping("/{id}/equipes")
    public List<EquipeApercu> listerEquipes(@PathVariable UUID id) {
        return clubService.listerEquipes(id);
    }
}
