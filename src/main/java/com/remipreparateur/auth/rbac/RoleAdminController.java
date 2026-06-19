package com.remipreparateur.auth.rbac;

import com.remipreparateur.auth.rbac.RbacDtos.*;
import com.remipreparateur.shared.security.CurrentUserProvider;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Administration des rôles & accès du club actif, réservée à {@code club:manage}
 * (président, et super-admin via bypass). Permet de gérer les rôles custom et
 * d'attribuer des rôles aux membres du staff.
 */
@RestController
@RequestMapping("/api/roles")
@PreAuthorize("hasAuthority('club:manage')")
public class RoleAdminController {

    private final RoleAdminService service;
    private final CurrentUserProvider currentUser;

    public RoleAdminController(RoleAdminService service, CurrentUserProvider currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    /** Catalogue figé des permissions (pour la matrice). */
    @GetMapping("/catalogue")
    public List<PermissionDto> catalogue() {
        return service.catalogue();
    }

    /** Rôles disponibles dans le club (système + custom). */
    @GetMapping
    public List<RoleDto> listerRoles() {
        return service.listerRoles(currentUser.current());
    }

    @PostMapping
    public ResponseEntity<RoleDto> creerRole(@Valid @RequestBody RoleUpsertRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creerRole(currentUser.current(), req));
    }

    @PutMapping("/{id}")
    public RoleDto majRole(@PathVariable UUID id, @Valid @RequestBody RoleUpsertRequest req) {
        return service.majRole(currentUser.current(), id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimerRole(@PathVariable UUID id) {
        service.supprimerRole(currentUser.current(), id);
        return ResponseEntity.noContent().build();
    }

    // ── Affectations d'un membre ──

    @GetMapping("/membres/{membreId}")
    public List<AffectationDto> listerAffectations(@PathVariable UUID membreId) {
        return service.listerAffectations(currentUser.current(), membreId);
    }

    @PutMapping("/membres/{membreId}")
    public List<AffectationDto> definirRoles(@PathVariable UUID membreId,
                                             @Valid @RequestBody DefinirRolesRequest req) {
        return service.definirRoles(currentUser.current(), membreId, req);
    }
}
