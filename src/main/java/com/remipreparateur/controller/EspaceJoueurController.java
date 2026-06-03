package com.remipreparateur.controller;

import com.remipreparateur.dto.BlessureDtos.BlessureResponse;
import com.remipreparateur.dto.EspaceJoueurDtos.MaPeseeResponse;
import com.remipreparateur.dto.GpsHistoriqueDto;
import com.remipreparateur.entity.Joueur;
import com.remipreparateur.repository.HistoriquePoidsRepository;
import com.remipreparateur.security.CurrentUserProvider;
import com.remipreparateur.service.BlessureService;
import com.remipreparateur.service.JoueurService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Espace personnel du joueur connecte : ses donnees uniquement (scoping par joueurId du token).
 * Reserve au role JOUEUR.
 */
@RestController
@RequestMapping("/api/moi")
@PreAuthorize("hasRole('JOUEUR')")
public class EspaceJoueurController {

    private final CurrentUserProvider currentUser;
    private final JoueurService joueurService;
    private final HistoriquePoidsRepository historiquePoidsRepository;
    private final BlessureService blessureService;

    public EspaceJoueurController(CurrentUserProvider currentUser,
                                  JoueurService joueurService,
                                  HistoriquePoidsRepository historiquePoidsRepository,
                                  BlessureService blessureService) {
        this.currentUser = currentUser;
        this.joueurService = joueurService;
        this.historiquePoidsRepository = historiquePoidsRepository;
        this.blessureService = blessureService;
    }

    @GetMapping("/profil")
    public Joueur profil() {
        UUID joueurId = monJoueurId();
        return joueurService.findById(joueurId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fiche joueur introuvable"));
    }

    @GetMapping("/gps")
    public List<GpsHistoriqueDto> mesSeancesGps() {
        return joueurService.getHistoriqueGps(monJoueurId());
    }

    @GetMapping("/pesees")
    public List<MaPeseeResponse> mesPesees() {
        return historiquePoidsRepository.findByJoueurIdOrderByDateDesc(monJoueurId()).stream()
                .map(h -> new MaPeseeResponse(h.getDate(), h.getPoids(), h.getCommentaire()))
                .toList();
    }

    @GetMapping("/blessures")
    public List<BlessureResponse> mesBlessures() {
        return blessureService.lister(monJoueurId());
    }

    /** joueurId du compte connecte, ou 409 si le compte n'est pas rattache a une fiche. */
    private UUID monJoueurId() {
        UUID joueurId = currentUser.current().getJoueurId();
        if (joueurId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Compte non rattache a une fiche joueur");
        }
        return joueurId;
    }
}
