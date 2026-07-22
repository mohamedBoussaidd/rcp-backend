package com.remipreparateur.performance.importation.controller;

import com.remipreparateur.performance.importation.dto.AnalyseImportHooperResponse;
import com.remipreparateur.performance.importation.dto.ConfirmerImportHooperRequest;
import com.remipreparateur.performance.importation.service.ImportHooperService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/**
 * Import du ressenti quotidien / Hooper (export « playermonitoring ») en deux temps :
 *  1. POST /analyser  : lit le fichier, détecte les colonnes, convertit (inversion d'échelle,
 *                       stress neutre, gêne), apparie les noms → aperçu (statut PRET).
 *  2. POST /confirmer : upsert wellness_quotidien après résolution des joueurs inconnus.
 * Gardé par hooper:import (SecurityConfig) + périmètre de l'équipe choisie (ScopeResolver).
 */
@RestController
@RequestMapping("/api/import-hooper")
@RequiredArgsConstructor
public class ImportHooperController {

    private final ImportHooperService importHooperService;

    @PostMapping("/analyser")
    public ResponseEntity<?> analyser(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "texte", required = false) String texte,
            @RequestParam("equipeId") UUID equipeId) {

        if ((file == null || file.isEmpty()) && (texte == null || texte.isBlank())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Fichier ou texte requis"));
        }
        try {
            byte[] contenu = file != null && !file.isEmpty() ? file.getBytes() : null;
            String nomFichier = file != null ? file.getOriginalFilename() : null;
            AnalyseImportHooperResponse reponse = importHooperService.analyser(contenu, nomFichier, texte, equipeId);
            return ResponseEntity.ok(reponse);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage() == null ? "Erreur d'analyse" : e.getMessage()));
        }
    }

    @PostMapping("/confirmer")
    public ResponseEntity<?> confirmer(@RequestBody ConfirmerImportHooperRequest request) {
        try {
            return ResponseEntity.ok(importHooperService.confirmer(request));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage() == null ? "Erreur lors de l'import" : e.getMessage()));
        }
    }
}
