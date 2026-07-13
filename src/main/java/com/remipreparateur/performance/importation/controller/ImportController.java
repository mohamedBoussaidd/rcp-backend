package com.remipreparateur.performance.importation.controller;

import com.remipreparateur.performance.importation.dto.AnalyseImportResponse;
import com.remipreparateur.performance.importation.dto.ConfirmerImportRequest;
import com.remipreparateur.performance.importation.service.ImportGpsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/**
 * Import GPS flexible (xlsx, CSV ou texte collé) en deux temps :
 *  1. POST /analyser : lit le fichier ; profil reconnu par signature → lignes converties +
 *     avertissements (statut PRET) ; format inconnu → colonnes + suggestions de mapping
 *     (statut MAPPING_REQUIS), le front revalide avec ses mappings (+ enregistrerProfil).
 *  2. POST /confirmer : écrit les données après résolution des joueurs inconnus.
 * Gardé par gps:import (SecurityConfig) + périmètre équipe de la séance (ScopeResolver).
 */
@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
public class ImportController {

    private final ImportGpsService importGpsService;

    @PostMapping("/analyser")
    public ResponseEntity<?> analyser(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "texte", required = false) String texte,
            @RequestParam("seanceId") UUID seanceId,
            @RequestParam(value = "mappings", required = false) String mappingsJson,
            @RequestParam(value = "formatIdentite", required = false) String formatIdentite,
            @RequestParam(value = "enregistrerProfil", defaultValue = "false") boolean enregistrerProfil,
            @RequestParam(value = "nomProfil", required = false) String nomProfil) {

        if ((file == null || file.isEmpty()) && (texte == null || texte.isBlank())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Fichier ou texte requis"));
        }
        try {
            byte[] contenu = file != null && !file.isEmpty() ? file.getBytes() : null;
            String nomFichier = file != null ? file.getOriginalFilename() : null;
            AnalyseImportResponse reponse = importGpsService.analyser(
                    contenu, nomFichier, texte, seanceId, mappingsJson, formatIdentite, enregistrerProfil, nomProfil);
            return ResponseEntity.ok(reponse);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage() == null ? "Erreur d'analyse" : e.getMessage()));
        }
    }

    @PostMapping("/confirmer")
    public ResponseEntity<?> confirmer(@RequestBody ConfirmerImportRequest request) {
        try {
            return ResponseEntity.ok(importGpsService.confirmer(request));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage() == null ? "Erreur lors de l'import" : e.getMessage()));
        }
    }

    @GetMapping("/profils")
    public ResponseEntity<?> profils(@RequestParam("seanceId") UUID seanceId) {
        try {
            return ResponseEntity.ok(importGpsService.listerProfils(seanceId));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/profils/{id}")
    public ResponseEntity<?> supprimerProfil(@PathVariable UUID id, @RequestParam("seanceId") UUID seanceId) {
        try {
            importGpsService.supprimerProfil(id, seanceId);
            return ResponseEntity.noContent().build();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
