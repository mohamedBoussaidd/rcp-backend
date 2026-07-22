package com.remipreparateur.performance.importation.controller;

import com.remipreparateur.performance.importation.dto.AnalyseImportRpeResponse;
import com.remipreparateur.performance.importation.dto.ConfirmerImportRpeRequest;
import com.remipreparateur.performance.importation.service.ImportRpeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/**
 * Import du RPE/ressenti post-séance (fichier questionnaire) en deux temps :
 *  1. POST /analyser  : lit le fichier, détecte les colonnes, apparie les noms → aperçu (statut PRET).
 *  2. POST /confirmer : upsert des RPE après résolution des joueurs inconnus.
 * Gardé par rpe:import (SecurityConfig) + périmètre équipe de la séance (ScopeResolver).
 */
@RestController
@RequestMapping("/api/import-rpe")
@RequiredArgsConstructor
public class ImportRpeController {

    private final ImportRpeService importRpeService;

    @PostMapping("/analyser")
    public ResponseEntity<?> analyser(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "texte", required = false) String texte,
            @RequestParam("seanceId") UUID seanceId) {

        if ((file == null || file.isEmpty()) && (texte == null || texte.isBlank())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Fichier ou texte requis"));
        }
        try {
            byte[] contenu = file != null && !file.isEmpty() ? file.getBytes() : null;
            String nomFichier = file != null ? file.getOriginalFilename() : null;
            AnalyseImportRpeResponse reponse = importRpeService.analyser(contenu, nomFichier, texte, seanceId);
            return ResponseEntity.ok(reponse);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage() == null ? "Erreur d'analyse" : e.getMessage()));
        }
    }

    @PostMapping("/confirmer")
    public ResponseEntity<?> confirmer(@RequestBody ConfirmerImportRpeRequest request) {
        try {
            return ResponseEntity.ok(importRpeService.confirmer(request));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage() == null ? "Erreur lors de l'import" : e.getMessage()));
        }
    }
}
