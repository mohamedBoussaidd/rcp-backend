package com.remipreparateur.performance.seance.controller;

import com.remipreparateur.performance.seance.dto.PresenceDtos.AssiduiteJoueur;
import com.remipreparateur.performance.seance.dto.PresenceDtos.HistoriqueEquipe;
import com.remipreparateur.performance.seance.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Page dédiée « Présence » (historique filtrable). Isolé sous {@code /api/presence} pour être gardé
 * par la permission {@code presence:write} (cf. SecurityConfig) — au lieu de {@code seances:read} qui
 * couvre toutes les lectures sous {@code /api/seances/**}. Le scope équipe est résolu dans le service.
 */
@RestController
@RequestMapping("/api/presence")
@RequiredArgsConstructor
public class PresenceHistoriqueController {

    private final PresenceService presenceService;

    /** Mode Équipe : une ligne par entraînement de la fenêtre (saison / période libre). */
    @GetMapping("/historique/equipe")
    public ResponseEntity<HistoriqueEquipe> historiqueEquipe(
            @RequestParam(required = false) UUID saisonId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au) {
        return ResponseEntity.ok(presenceService.historiqueEquipe(saisonId, du, au));
    }

    /** Mode Joueur : bilan + historique d'un joueur sur la fenêtre (saison / période libre). */
    @GetMapping("/historique/joueur")
    public ResponseEntity<AssiduiteJoueur> historiqueJoueur(
            @RequestParam UUID joueurId,
            @RequestParam(required = false) UUID saisonId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au) {
        return ResponseEntity.ok(presenceService.historiqueJoueur(joueurId, saisonId, du, au));
    }
}
