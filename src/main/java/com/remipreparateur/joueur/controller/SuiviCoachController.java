package com.remipreparateur.joueur.controller;

import com.remipreparateur.joueur.dto.SuiviCoachDtos.*;
import com.remipreparateur.joueur.service.SuiviCoachService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Relation entraîneur ↔ joueur : fil de vie, objectifs individuels, notes du staff (V98).
 *
 * <p>Servi sous {@code /api/suivi-coach/**} et NON sous {@code /api/joueurs/**} : ce préfixe est
 * déjà gardé par {@code joueurs:read} / {@code joueurs:write}, qui auraient absorbé ces routes et
 * rendu la permission {@code suivi_coach:*} inopérante.
 */
@RestController
@RequestMapping("/api/suivi-coach")
@RequiredArgsConstructor
public class SuiviCoachController {

    private final SuiviCoachService service;

    // ── Fil de vie ──
    @GetMapping("/joueurs/{joueurId}/fil-de-vie")
    public FilDeVie filDeVie(@PathVariable UUID joueurId,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate depuis) {
        return service.filDeVie(joueurId, depuis);
    }

    // ── Objectifs ──
    @GetMapping("/joueurs/{joueurId}/objectifs")
    public List<ObjectifResponse> objectifs(@PathVariable UUID joueurId) {
        return service.objectifs(joueurId);
    }

    @PostMapping("/joueurs/{joueurId}/objectifs")
    public ResponseEntity<ObjectifResponse> creerObjectif(@PathVariable UUID joueurId,
                                                          @Valid @RequestBody ObjectifRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creerObjectif(joueurId, req));
    }

    @PutMapping("/objectifs/{objectifId}")
    public ObjectifResponse modifierObjectif(@PathVariable UUID objectifId, @Valid @RequestBody ObjectifRequest req) {
        return service.modifierObjectif(objectifId, req);
    }

    @DeleteMapping("/objectifs/{objectifId}")
    public ResponseEntity<Void> supprimerObjectif(@PathVariable UUID objectifId) {
        service.supprimerObjectif(objectifId);
        return ResponseEntity.noContent().build();
    }

    // ── Notes du staff ──
    @GetMapping("/joueurs/{joueurId}/notes")
    public List<NoteResponse> notes(@PathVariable UUID joueurId) {
        return service.notes(joueurId);
    }

    @PostMapping("/joueurs/{joueurId}/notes")
    public ResponseEntity<NoteResponse> creerNote(@PathVariable UUID joueurId, @Valid @RequestBody NoteRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creerNote(joueurId, req));
    }

    @DeleteMapping("/notes/{noteId}")
    public ResponseEntity<Void> supprimerNote(@PathVariable UUID noteId) {
        service.supprimerNote(noteId);
        return ResponseEntity.noContent().build();
    }
}
