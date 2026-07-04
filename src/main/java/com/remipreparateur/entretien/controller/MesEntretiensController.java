package com.remipreparateur.entretien.controller;

import com.remipreparateur.auth.rbac.FeatureModule;
import com.remipreparateur.auth.rbac.PermissionResolver;
import com.remipreparateur.club.pack.ClubModulesService;
import com.remipreparateur.entretien.dto.EntretienDtos.*;
import com.remipreparateur.entretien.service.EntretienService;
import com.remipreparateur.shared.security.CurrentUserProvider;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Espace joueur — son suivi individuel (self-scope par joueurId du token). Réservé au rôle JOUEUR ;
 * l'accès est aussi conditionné à l'activation du module « Suivi individuel » pour son club.
 */
@RestController
@RequestMapping("/api/moi")
@PreAuthorize("hasRole('JOUEUR')")
public class MesEntretiensController {

    private final EntretienService service;
    private final CurrentUserProvider currentUser;
    private final PermissionResolver permissionResolver;
    private final ClubModulesService clubModulesService;

    public MesEntretiensController(EntretienService service, CurrentUserProvider currentUser,
                                   PermissionResolver permissionResolver, ClubModulesService clubModulesService) {
        this.service = service;
        this.currentUser = currentUser;
        this.permissionResolver = permissionResolver;
        this.clubModulesService = clubModulesService;
    }

    /** Mes axes de travail EN_COURS (avec la note staff si l'entretien source est partagé). */
    @GetMapping("/axes")
    public List<MonAxeResponse> mesAxes() {
        exigeModule();
        return service.mesAxes(monJoueurId());
    }

    /** Mes entretiens partagés par le staff (visibilite = PARTAGE_JOUEUR). */
    @GetMapping("/entretiens")
    public List<MonEntretienResponse> mesEntretiens() {
        exigeModule();
        return service.mesEntretiens(monJoueurId());
    }

    @GetMapping("/auto-evaluations")
    public List<AutoEvalResponse> mesAutoEvaluations() {
        exigeModule();
        return service.mesAutoEvaluations(monJoueurId());
    }

    /** Auto-évaluation sur un de mes axes EN_COURS (max une par axe et par semaine). */
    @PostMapping("/auto-evaluations")
    public AutoEvalResponse autoEvaluer(@Valid @RequestBody AutoEvalRequest req) {
        exigeModule();
        return service.autoEvaluer(monJoueurId(), req);
    }

    private void exigeModule() {
        UUID clubId = permissionResolver.clubActif(currentUser.current());
        if (!clubModulesService.modulesActifs(clubId).contains(FeatureModule.SUIVI_INDIVIDUEL.getCode())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Le module « " + FeatureModule.SUIVI_INDIVIDUEL.getLibelle() + " » n'est pas activé pour votre club.");
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
