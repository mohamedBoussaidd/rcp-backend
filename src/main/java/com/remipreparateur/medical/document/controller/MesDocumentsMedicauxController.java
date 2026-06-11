package com.remipreparateur.medical.document.controller;

import com.remipreparateur.medical.document.dto.DocumentMedicalDtos.DocumentMedicalResponse;
import com.remipreparateur.medical.document.dto.DocumentMedicalDtos.PartageRequest;
import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.medical.document.service.DocumentMedicalService;
import com.remipreparateur.medical.document.service.DocumentMedicalService.FichierDocument;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Espace joueur — ses documents medicaux (scoping par joueurId du token).
 * Reserve au role JOUEUR ; le joueur ne voit et ne gere que ses propres documents.
 */
@RestController
@RequestMapping("/api/moi/documents-medicaux")
@PreAuthorize("hasRole('JOUEUR')")
public class MesDocumentsMedicauxController {

    private final DocumentMedicalService service;
    private final CurrentUserProvider currentUser;

    public MesDocumentsMedicauxController(DocumentMedicalService service, CurrentUserProvider currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<DocumentMedicalResponse> lister() {
        return service.listerPourJoueur(monJoueurId());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentMedicalResponse> deposer(
            @RequestParam("fichier") MultipartFile fichier,
            @RequestParam("categorie") String categorie,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "partageRoles", required = false) List<String> partageRoles) {
        DocumentMedicalResponse res = service.deposer(monJoueurId(), fichier, categorie, description, partageRoles);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @GetMapping("/{id}/fichier")
    public ResponseEntity<Resource> telecharger(@PathVariable UUID id) {
        return reponseFichier(service.chargerPourJoueur(monJoueurId(), id));
    }

    @PatchMapping("/{id}/partage")
    public DocumentMedicalResponse modifierPartage(@PathVariable UUID id, @RequestBody PartageRequest req) {
        return service.modifierPartage(monJoueurId(), id, req.partageRoles());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID id) {
        service.supprimerParJoueur(monJoueurId(), id);
        return ResponseEntity.noContent().build();
    }

    static ResponseEntity<Resource> reponseFichier(FichierDocument f) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.typeMime()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + f.nomOriginal().replace("\"", "") + "\"")
                .body(f.resource());
    }

    private UUID monJoueurId() {
        UUID joueurId = currentUser.current().getJoueurId();
        if (joueurId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Compte non rattache a une fiche joueur");
        }
        return joueurId;
    }
}
