package com.remipreparateur.joueur.controller;

import com.remipreparateur.medical.blessure.dto.BlessureDtos.BlessureResponse;
import com.remipreparateur.medical.blessure.dto.BlessureSuiviDtos.EtapeResponse;
import com.remipreparateur.joueur.dto.EspaceJoueurDtos.MaPeseeResponse;
import com.remipreparateur.performance.gps.dto.GpsHistoriqueDto;
import com.remipreparateur.performance.rpe.dto.RpeDtos.RpeRequest;
import com.remipreparateur.performance.rpe.dto.RpeDtos.RpeResponse;
import com.remipreparateur.tactical.seancetechnique.dto.SeanceTechniqueDtos.SeanceTechniqueResponse;
import com.remipreparateur.medical.wellness.dto.WellnessDtos.WellnessRequest;
import com.remipreparateur.medical.wellness.dto.WellnessDtos.WellnessResponse;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.performance.seance.entity.Seance;
import com.remipreparateur.performance.poids.repository.HistoriquePoidsRepository;
import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.medical.blessure.service.BlessureService;
import com.remipreparateur.medical.blessure.service.BlessureSuiviService;
import com.remipreparateur.joueur.service.JoueurService;
import com.remipreparateur.performance.rpe.service.RpeService;
import com.remipreparateur.performance.seance.service.SeanceService;
import com.remipreparateur.tactical.seancetechnique.service.SeanceTechniqueService;
import com.remipreparateur.medical.wellness.service.WellnessService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
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
    private final SeanceService seanceService;
    private final SeanceTechniqueService seanceTechniqueService;
    private final WellnessService wellnessService;
    private final RpeService rpeService;
    private final BlessureSuiviService blessureSuiviService;

    public EspaceJoueurController(CurrentUserProvider currentUser,
                                  JoueurService joueurService,
                                  HistoriquePoidsRepository historiquePoidsRepository,
                                  BlessureService blessureService,
                                  SeanceService seanceService,
                                  SeanceTechniqueService seanceTechniqueService,
                                  WellnessService wellnessService,
                                  RpeService rpeService,
                                  BlessureSuiviService blessureSuiviService) {
        this.currentUser = currentUser;
        this.joueurService = joueurService;
        this.historiquePoidsRepository = historiquePoidsRepository;
        this.blessureService = blessureService;
        this.seanceService = seanceService;
        this.seanceTechniqueService = seanceTechniqueService;
        this.wellnessService = wellnessService;
        this.rpeService = rpeService;
        this.blessureSuiviService = blessureSuiviService;
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

    /** Protocole de reprise (RTP) d'une de mes blessures — lecture seule. */
    @GetMapping("/blessures/{blessureId}/rtp")
    public List<EtapeResponse> mesEtapesRtp(@PathVariable UUID blessureId) {
        return blessureSuiviService.listerRtpPourJoueur(monJoueurId(), blessureId);
    }

    /**
     * Seances de l'equipe du joueur (lecture seule). Scoping automatique a son equipe via
     * ScopeResolver (role JOUEUR). Avec debut+fin : vue calendrier sur une periode.
     */
    @GetMapping("/seances")
    public List<Seance> mesSeances(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return (debut != null && fin != null)
                ? seanceService.findByPeriode(debut, fin)
                : seanceService.findAll();
    }

    /** Séances techniques de l'équipe du joueur (lecture seule, scoping via ScopeResolver). */
    @GetMapping("/seances-techniques")
    public List<SeanceTechniqueResponse> mesSeancesTechniques(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return seanceTechniqueService.lister(debut, fin);
    }

    // ──────────────────────────── Wellness (ressenti quotidien) ────────────────────────────

    @GetMapping("/wellness")
    public List<WellnessResponse> mesWellness() {
        return wellnessService.listerPourJoueur(monJoueurId());
    }

    /** Saisie/mise a jour du ressenti du jour (upsert sur joueur + date). */
    @PostMapping("/wellness")
    public WellnessResponse saisirWellness(@Valid @RequestBody WellnessRequest req) {
        return wellnessService.enregistrer(monJoueurId(), req);
    }

    // ──────────────────────────── RPE de seance ────────────────────────────

    @GetMapping("/rpe")
    public List<RpeResponse> mesRpe() {
        return rpeService.listerPourJoueur(monJoueurId());
    }

    /** Saisie/mise a jour du RPE d'une seance (upsert sur joueur + seance). */
    @PostMapping("/rpe")
    public RpeResponse saisirRpe(@Valid @RequestBody RpeRequest req) {
        return rpeService.enregistrer(monJoueurId(), req);
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
