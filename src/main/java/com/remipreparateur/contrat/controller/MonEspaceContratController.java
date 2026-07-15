package com.remipreparateur.contrat.controller;

import com.remipreparateur.auth.rbac.FeatureModule;
import com.remipreparateur.auth.rbac.PermissionResolver;
import com.remipreparateur.club.pack.ClubModulesService;
import com.remipreparateur.contrat.dto.ContratDtos.MonBulletin;
import com.remipreparateur.contrat.dto.ContratDtos.MonContrat;
import com.remipreparateur.contrat.service.BulletinPaieService;
import com.remipreparateur.contrat.service.ContratService;
import com.remipreparateur.shared.security.CurrentUserProvider;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * MES contrats et MES fiches de paye (self-scope par la fiche du compte, joueur OU staff).
 * Monté sous /api/membre : accessible à tout compte authentifié relié à une fiche —
 * même mécanique que MesDocumentsAdminController (V58). Un bulletin n'est visible qu'une
 * fois distribué ; son 1er téléchargement est timbré (suivi côté gestionnaire).
 */
@RestController
@RequestMapping("/api/membre")
public class MonEspaceContratController {

    private final ContratService contratService;
    private final BulletinPaieService bulletinService;
    private final CurrentUserProvider currentUser;
    private final PermissionResolver permissionResolver;
    private final ClubModulesService clubModulesService;

    public MonEspaceContratController(ContratService contratService, BulletinPaieService bulletinService,
                                      CurrentUserProvider currentUser, PermissionResolver permissionResolver,
                                      ClubModulesService clubModulesService) {
        this.contratService = contratService;
        this.bulletinService = bulletinService;
        this.currentUser = currentUser;
        this.permissionResolver = permissionResolver;
        this.clubModulesService = clubModulesService;
    }

    @GetMapping("/contrats")
    public List<MonContrat> mesContrats() {
        exigeModule();
        return contratService.mesContrats(maFiche());
    }

    @GetMapping("/contrats/{id}/fichier")
    public ResponseEntity<Resource> telechargerContrat(@PathVariable UUID id) {
        exigeModule();
        return ContratController.reponseFichier(contratService.chargerMonFichier(maFiche(), id));
    }

    @GetMapping("/bulletins")
    public List<MonBulletin> mesBulletins() {
        exigeModule();
        return bulletinService.mesBulletins(maFiche());
    }

    @GetMapping("/bulletins/{id}/fichier")
    public ResponseEntity<Resource> telechargerBulletin(@PathVariable UUID id) {
        exigeModule();
        return ContratController.reponseFichier(bulletinService.chargerMonBulletin(maFiche(), id));
    }

    private void exigeModule() {
        UUID clubId = permissionResolver.clubActif(currentUser.current());
        if (!clubModulesService.modulesActifs(clubId).contains(FeatureModule.CONTRATS.getCode())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Le module « " + FeatureModule.CONTRATS.getLibelle() + " » n'est pas activé pour votre club.");
        }
    }

    private UUID maFiche() {
        UUID joueurId = currentUser.current().getJoueurId();
        if (joueurId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Compte non rattaché à une fiche");
        }
        return joueurId;
    }
}
