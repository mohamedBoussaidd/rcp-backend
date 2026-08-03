package com.remipreparateur.tactical.schema.controller;

import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.tactical.schema.dto.SchemaPartageDtos.MonSchemaResponse;
import com.remipreparateur.tactical.schema.service.SchemaPartageService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Espace joueur — les schémas que le staff lui a partagés (self-scope par le joueurId du token).
 * Le module {@code schemas_joueur} est vérifié dans le service.
 */
@RestController
@RequestMapping("/api/moi")
@PreAuthorize("hasRole('JOUEUR')")
public class MesSchemasController {

    private final SchemaPartageService service;
    private final CurrentUserProvider currentUser;

    public MesSchemasController(SchemaPartageService service, CurrentUserProvider currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/schemas")
    public List<MonSchemaResponse> mesSchemas() {
        return service.mesSchemas(monJoueurId());
    }

    private UUID monJoueurId() {
        UUID joueurId = currentUser.current().getJoueurId();
        if (joueurId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Compte non rattaché à une fiche joueur");
        }
        return joueurId;
    }
}
