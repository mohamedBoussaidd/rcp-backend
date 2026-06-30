package com.remipreparateur.auth.rbac;

import com.remipreparateur.auth.rbac.RbacDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Administration des rôles GLOBAUX (prédéfinis système + rôles globaux custom), réservée
 * au SUPER_ADMIN. Contrairement à {@link RoleAdminController} (rôles d'UN club, gérés par le
 * président via {@code /api/roles}), cet écran agit hors de tout club : il permet d'éditer les
 * permissions des rôles prédéfinis et de créer / modifier / supprimer des rôles globaux
 * réutilisables par tous les clubs. Les présidents peuvent ensuite ATTRIBUER ces rôles globaux,
 * sans pouvoir les modifier.
 */
@RestController
@RequestMapping("/api/admin/roles-globaux")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class RoleGlobalAdminController {

    private final RoleAdminService service;

    public RoleGlobalAdminController(RoleAdminService service) {
        this.service = service;
    }

    /** Catalogue figé des permissions (pour la matrice). */
    @GetMapping("/catalogue")
    public List<PermissionDto> catalogue() {
        return service.catalogue();
    }

    /** Rôles globaux : prédéfinis système + globaux custom. */
    @GetMapping
    public List<RoleDto> lister() {
        return service.listerGlobaux();
    }

    @PostMapping
    public ResponseEntity<RoleDto> creer(@Valid @RequestBody RoleUpsertRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creerGlobal(req));
    }

    @PutMapping("/{id}")
    public RoleDto modifier(@PathVariable UUID id, @Valid @RequestBody RoleUpsertRequest req) {
        return service.majGlobal(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID id) {
        service.supprimerGlobal(id);
        return ResponseEntity.noContent().build();
    }
}
