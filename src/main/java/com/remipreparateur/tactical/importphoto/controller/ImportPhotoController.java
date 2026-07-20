package com.remipreparateur.tactical.importphoto.controller;

import com.remipreparateur.tactical.importphoto.dto.ImportPhotoDtos.ImportPhotoResponse;
import com.remipreparateur.tactical.importphoto.service.ImportPhotoService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Import d'une séance/exercice depuis une photo (IA vision). Gardé par
 * `import_photo:use` (module import_photo_ia — cf. SecurityConfig) : l'appel
 * API Anthropic part TOUJOURS du backend, jamais du front.
 */
@RestController
@RequestMapping("/api/import-photo")
public class ImportPhotoController {

    private final ImportPhotoService service;

    public ImportPhotoController(ImportPhotoService service) {
        this.service = service;
    }

    /** Analyse la photo (10-20 s) et renvoie le contenu extrait + le schéma prêt pour l'éditeur. */
    @PostMapping
    public ImportPhotoResponse importer(@RequestParam("photo") MultipartFile photo) {
        return service.importer(photo);
    }

    /** Photo d'origine d'un import (pièce jointe de l'exercice créé). */
    @GetMapping("/{journalId}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable UUID journalId) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(service.photo(journalId));
    }
}
