package com.remipreparateur.medical.document.controller;

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
 * Module medical (staff) — consultation des documents medicaux des joueurs.
 * Lecture : staff (regle SecurityConfig) ; filtrage fin par visibilite (role ∈ partage,
 * ou MEDICAL/SUPER_ADMIN) et par perimetre d'equipe dans le service.
 * Suppression : MEDICAL + SUPER_ADMIN.
 */
@RestController
@RequestMapping("/api/documents-medicaux")
public class DocumentMedicalController {

    private final DocumentMedicalService service;

    public DocumentMedicalController(DocumentMedicalService service) {
        this.service = service;
    }

    @GetMapping
    public List<DocumentMedicalResponse> lister(@RequestParam(required = false) UUID joueurId) {
        return service.listerPourStaff(joueurId);
    }

    /** Dépôt d'un document par le staff pour un joueur (MEDICAL / SUPER_ADMIN — cf. SecurityConfig). */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentMedicalResponse> deposer(
            @RequestParam("joueurId") UUID joueurId,
            @RequestParam("fichier") MultipartFile fichier,
            @RequestParam("categorie") String categorie,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "partageRoles", required = false) List<String> partageRoles) {
        DocumentMedicalResponse res = service.deposer(joueurId, fichier, categorie, description, partageRoles);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @GetMapping("/{id}/fichier")
    public ResponseEntity<Resource> telecharger(@PathVariable UUID id) {
        return MesDocumentsMedicauxController.reponseFichier(service.chargerPourStaff(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID id) {
        service.supprimerParStaff(id);
        return ResponseEntity.noContent().build();
    }
}
