package com.remipreparateur.documentadmin.controller;

import com.remipreparateur.auth.rbac.FeatureModule;
import com.remipreparateur.auth.rbac.PermissionResolver;
import com.remipreparateur.club.pack.ClubModulesService;
import com.remipreparateur.documentadmin.dto.DocumentAdminDtos.MonDocumentResponse;
import com.remipreparateur.documentadmin.service.DocumentAdminService;
import com.remipreparateur.shared.security.CurrentUserProvider;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Mes documents administratifs (self-scope par joueurId du token — la fiche personne).
 * Deux montages : {@code /api/moi} (joueur, JOUEUR-only via SecurityConfig) et {@code /api/membre}
 * (tout compte authentifié relié à une fiche — utilisé par l'espace staff, V58). Le self-scope
 * rend l'ouverture sûre : chacun ne voit que les documents de SA fiche. L'accès reste conditionné
 * à l'activation du module « Licences & documents ».
 */
@RestController
@RequestMapping({"/api/moi", "/api/membre"})
public class MesDocumentsAdminController {

    private final DocumentAdminService service;
    private final CurrentUserProvider currentUser;
    private final PermissionResolver permissionResolver;
    private final ClubModulesService clubModulesService;

    public MesDocumentsAdminController(DocumentAdminService service, CurrentUserProvider currentUser,
                                       PermissionResolver permissionResolver, ClubModulesService clubModulesService) {
        this.service = service;
        this.currentUser = currentUser;
        this.permissionResolver = permissionResolver;
        this.clubModulesService = clubModulesService;
    }

    @GetMapping("/documents-administratifs")
    public List<MonDocumentResponse> mesDocuments() {
        exigeModule();
        return service.mesDocuments(monJoueurId());
    }

    @PostMapping(value = "/documents-administratifs/{typeId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MonDocumentResponse deposer(@PathVariable UUID typeId, @RequestParam("fichier") MultipartFile fichier) {
        exigeModule();
        return service.deposerParJoueur(monJoueurId(), typeId, fichier);
    }

    @GetMapping("/documents-administratifs/{id}/fichier")
    public ResponseEntity<Resource> telecharger(@PathVariable UUID id) {
        exigeModule();
        return DocumentAdminController.reponseFichier(service.chargerPourJoueur(monJoueurId(), id));
    }

    private void exigeModule() {
        UUID clubId = permissionResolver.clubActif(currentUser.current());
        if (!clubModulesService.modulesActifs(clubId).contains(FeatureModule.DOCUMENTS_ADMIN.getCode())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Le module « " + FeatureModule.DOCUMENTS_ADMIN.getLibelle() + " » n'est pas activé pour votre club.");
        }
    }

    private UUID monJoueurId() {
        UUID joueurId = currentUser.current().getJoueurId();
        if (joueurId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Compte non rattaché à une fiche joueur");
        }
        return joueurId;
    }
}
