package com.remipreparateur.medical.document.controller;

import com.remipreparateur.medical.document.dto.DocumentMedicalDtos.DocumentMedicalResponse;
import com.remipreparateur.medical.document.service.DocumentMedicalService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
