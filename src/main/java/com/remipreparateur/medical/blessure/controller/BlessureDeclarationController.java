package com.remipreparateur.medical.blessure.controller;

import com.remipreparateur.medical.document.controller.MesDocumentsMedicauxController;
import com.remipreparateur.medical.document.dto.DocumentMedicalDtos.DocumentMedicalResponse;
import com.remipreparateur.medical.document.service.DocumentMedicalService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Déclarations administratives d'une blessure (arrêt maladie / accident de travail) :
 * documents médicaux rattachés à la blessure. Gardé par blessures:qualify
 * (MEDICAL + PRESIDENT + ADMINISTRATIF — cf. SecurityConfig).
 */
@RestController
@RequestMapping("/api/blessures/{blessureId}/declarations")
public class BlessureDeclarationController {

    private final DocumentMedicalService service;

    public BlessureDeclarationController(DocumentMedicalService service) {
        this.service = service;
    }

    @GetMapping
    public List<DocumentMedicalResponse> lister(@PathVariable UUID blessureId) {
        return service.listerDeclarations(blessureId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentMedicalResponse> deposer(
            @PathVariable UUID blessureId,
            @RequestParam("fichier") MultipartFile fichier,
            @RequestParam("categorie") String categorie,
            @RequestParam(value = "description", required = false) String description) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.deposerDeclaration(blessureId, fichier, categorie, description));
    }

    @GetMapping("/{id}/fichier")
    public ResponseEntity<Resource> telecharger(@PathVariable UUID blessureId, @PathVariable UUID id) {
        return MesDocumentsMedicauxController.reponseFichier(service.chargerDeclaration(blessureId, id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID blessureId, @PathVariable UUID id) {
        service.supprimerDeclaration(blessureId, id);
        return ResponseEntity.noContent().build();
    }
}
