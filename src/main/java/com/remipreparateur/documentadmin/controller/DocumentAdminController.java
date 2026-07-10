package com.remipreparateur.documentadmin.controller;

import com.remipreparateur.documentadmin.dto.DocumentAdminDtos.ConformiteResponse;
import com.remipreparateur.documentadmin.dto.DocumentAdminDtos.DocumentResponse;
import com.remipreparateur.documentadmin.dto.DocumentAdminDtos.RefusRequest;
import com.remipreparateur.documentadmin.service.DocumentAdminService;
import com.remipreparateur.documentadmin.service.DocumentAdminService.FichierDocument;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Licences & documents (staff) : matrice de conformité, dépôt pour un joueur, validation/refus,
 * téléchargement. Droits résolus par SecurityConfig (docadmin:read/upload/validate) ; le
 * périmètre équipe/club reste filtré par {@code ScopeResolver} dans le service.
 */
@RestController
@RequestMapping("/api/documents-admin")
public class DocumentAdminController {

    private final DocumentAdminService service;

    public DocumentAdminController(DocumentAdminService service) {
        this.service = service;
    }

    @GetMapping("/conformite")
    public ConformiteResponse conformite(@RequestParam(required = false) UUID equipeId) {
        return service.matrice(equipeId);
    }

    /** Conformité documentaire du staff (encadrants) du club. */
    @GetMapping("/conformite-staff")
    public ConformiteResponse conformiteStaff() {
        return service.matriceStaff();
    }

    @PostMapping(value = "/joueurs/{joueurId}/types/{typeId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> deposer(@PathVariable UUID joueurId, @PathVariable UUID typeId,
                                                     @RequestParam("fichier") MultipartFile fichier) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.deposerParStaff(joueurId, typeId, fichier));
    }

    @PostMapping("/{id}/valider")
    public DocumentResponse valider(@PathVariable UUID id) {
        return service.valider(id);
    }

    @PostMapping("/{id}/refuser")
    public DocumentResponse refuser(@PathVariable UUID id, @RequestBody RefusRequest req) {
        return service.refuser(id, req.motif());
    }

    @GetMapping("/{id}/fichier")
    public ResponseEntity<Resource> telecharger(@PathVariable UUID id) {
        return reponseFichier(service.chargerPourStaff(id));
    }

    static ResponseEntity<Resource> reponseFichier(FichierDocument f) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.typeMime()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + f.nomOriginal().replace("\"", "") + "\"")
                .body(f.resource());
    }
}
