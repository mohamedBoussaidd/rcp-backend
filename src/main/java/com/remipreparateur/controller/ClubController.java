package com.remipreparateur.controller;

import com.remipreparateur.dto.ClubDtos.ClubCreateRequest;
import com.remipreparateur.dto.ClubDtos.ClubResponse;
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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID id) {
        clubService.supprimerClub(id);
        return ResponseEntity.noContent().build();
    }
}
