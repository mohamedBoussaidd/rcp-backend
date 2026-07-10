package com.remipreparateur.documentadmin.controller;

import com.remipreparateur.documentadmin.dto.DocumentAdminDtos.TypeDocumentRequisRequest;
import com.remipreparateur.documentadmin.dto.DocumentAdminDtos.TypeDocumentRequisResponse;
import com.remipreparateur.documentadmin.service.DocumentAdminService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Référentiel des documents requis (licence, certificat médical...). Réservé à {@code docadmin:configure}. */
@RestController
@RequestMapping("/api/documents-admin/types")
public class TypeDocumentRequisController {

    private final DocumentAdminService service;

    public TypeDocumentRequisController(DocumentAdminService service) {
        this.service = service;
    }

    @GetMapping
    public List<TypeDocumentRequisResponse> lister() {
        return service.listerTypes();
    }

    @PostMapping
    public TypeDocumentRequisResponse creer(@Valid @RequestBody TypeDocumentRequisRequest req) {
        return service.creerType(req);
    }

    @PutMapping("/{id}")
    public TypeDocumentRequisResponse modifier(@PathVariable UUID id, @Valid @RequestBody TypeDocumentRequisRequest req) {
        return service.modifierType(id, req);
    }
}
