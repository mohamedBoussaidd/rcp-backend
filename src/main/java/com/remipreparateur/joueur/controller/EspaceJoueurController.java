package com.remipreparateur.joueur.controller;

import com.remipreparateur.medical.blessure.dto.BlessureDtos.BlessureResponse;
import com.remipreparateur.medical.blessure.dto.BlessureSuiviDtos.EtapeResponse;
import com.remipreparateur.joueur.dto.EspaceJoueurDtos.MaPeseeResponse;
import com.remipreparateur.joueur.dto.MaSeanceGpsDto;
import com.remipreparateur.shared.time.Horloge;
import com.remipreparateur.performance.calendrier.dto.CalendrierDtos.ContexteCalendrier;
import com.remipreparateur.performance.calendrier.service.CalendrierContexteService;
import com.remipreparateur.performance.evenement.dto.EvenementDtos.EvenementResponse;
import com.remipreparateur.performance.evenement.service.EvenementService;
import com.remipreparateur.performance.gps.dto.GpsHistoriqueDto;
import com.remipreparateur.medical.conseil.dto.ConseilDtos.ConseilResponse;
import com.remipreparateur.medical.conseil.service.ConseilService;
import com.remipreparateur.tactical.match.dto.MatchDtos.MatchJoueurResume;
import com.remipreparateur.tactical.match.dto.MatchDtos.MatchJoueurDetail;
import com.remipreparateur.tactical.match.service.MatchService;
import com.remipreparateur.performance.rpe.dto.RpeDtos.RpeRequest;
import com.remipreparateur.performance.rpe.dto.RpeDtos.RpeResponse;
import com.remipreparateur.medical.wellness.dto.WellnessDtos.WellnessRequest;
import com.remipreparateur.medical.wellness.dto.WellnessDtos.WellnessResponse;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.performance.seance.entity.Seance;
import com.remipreparateur.performance.seance.dto.SeanceDtos.ContenuSeance;
import com.remipreparateur.performance.seance.dto.PresenceDtos.DeclarationPresence;
import com.remipreparateur.performance.seance.dto.PresenceDtos.LignePresence;
import com.remipreparateur.performance.seance.dto.PresenceDtos.MaDeclaration;
import com.remipreparateur.performance.seance.service.PresenceService;
import com.remipreparateur.performance.poids.repository.HistoriquePoidsRepository;
import com.remipreparateur.auth.rbac.FeatureModule;
import com.remipreparateur.auth.rbac.PermissionResolver;
import com.remipreparateur.club.pack.ClubModulesService;
import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.shared.security.ScopeResolver;
import com.remipreparateur.medical.blessure.service.BlessureService;
import com.remipreparateur.medical.blessure.service.BlessureSuiviService;
import com.remipreparateur.joueur.service.JoueurService;
import com.remipreparateur.performance.rpe.service.RpeService;
import com.remipreparateur.performance.seance.dto.SeanceDtos.FicheSeanceJoueur;
import com.remipreparateur.performance.seance.service.SeanceFicheService;
import com.remipreparateur.performance.seance.dto.TypeSeanceDtos.TypeSeanceResponse;
import com.remipreparateur.performance.seance.service.SeanceService;
import com.remipreparateur.performance.seance.service.TypeSeanceCatalogueService;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final WellnessService wellnessService;
    private final RpeService rpeService;
    private final BlessureSuiviService blessureSuiviService;
    private final ConseilService conseilService;
    private final MatchService matchService;
    private final PresenceService presenceService;
    private final ScopeResolver scopeResolver;
    private final ClubModulesService clubModulesService;
    private final PermissionResolver permissionResolver;
    private final SeanceFicheService seanceFicheService;
    private final CalendrierContexteService calendrierContexteService;
    private final EvenementService evenementService;
    private final TypeSeanceCatalogueService typeSeanceCatalogueService;
    /** Date du jour de l'application (honore la date simulée du super-admin). */
    private final Horloge horloge;

    public EspaceJoueurController(CurrentUserProvider currentUser,
                                  JoueurService joueurService,
                                  HistoriquePoidsRepository historiquePoidsRepository,
                                  BlessureService blessureService,
                                  SeanceService seanceService,
                                  WellnessService wellnessService,
                                  RpeService rpeService,
                                  BlessureSuiviService blessureSuiviService,
                                  ConseilService conseilService,
                                  MatchService matchService,
                                  PresenceService presenceService,
                                  ScopeResolver scopeResolver,
                                  ClubModulesService clubModulesService,
                                  PermissionResolver permissionResolver,
                                  SeanceFicheService seanceFicheService,
                                  CalendrierContexteService calendrierContexteService,
                                  EvenementService evenementService,
                                  TypeSeanceCatalogueService typeSeanceCatalogueService,
                                  Horloge horloge) {
        this.horloge = horloge;
        this.calendrierContexteService = calendrierContexteService;
        this.evenementService = evenementService;
        this.typeSeanceCatalogueService = typeSeanceCatalogueService;
        this.currentUser = currentUser;
        this.joueurService = joueurService;
        this.historiquePoidsRepository = historiquePoidsRepository;
        this.blessureService = blessureService;
        this.seanceService = seanceService;
        this.wellnessService = wellnessService;
        this.rpeService = rpeService;
        this.blessureSuiviService = blessureSuiviService;
        this.conseilService = conseilService;
        this.matchService = matchService;
        this.presenceService = presenceService;
        this.scopeResolver = scopeResolver;
        this.clubModulesService = clubModulesService;
        this.permissionResolver = permissionResolver;
        this.seanceFicheService = seanceFicheService;
    }

    /**
     * Bloque (403) l'accès à un endpoint dont le module n'est pas activé pour le club du joueur.
     * Miroir serveur du masquage front : un club sans GPS/Médical/Match/… n'expose pas ces données
     * même via appel direct de l'API. Les données ne sont jamais supprimées, seulement inaccessibles.
     */
    private void exigeModule(FeatureModule module) {
        UUID clubId = permissionResolver.clubActif(currentUser.current());
        if (!clubModulesService.modulesActifs(clubId).contains(module.getCode())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Le module « " + module.getLibelle() + " » n'est pas activé pour votre club.");
        }
    }

    @GetMapping("/profil")
    public Joueur profil() {
        UUID joueurId = monJoueurId();
        return joueurService.findById(joueurId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fiche joueur introuvable"));
    }

    @GetMapping("/gps")
    public List<GpsHistoriqueDto> mesSeancesGps() {
        exigeModule(FeatureModule.GPS);
        return joueurService.getHistoriqueGps(monJoueurId());
    }

    /**
     * Mon historique de séances mesurées : les séances PASSÉES de mon équipe, avec mon GPS quand
     * il existe et mon statut d'appel sinon.
     *
     * <p>Assemblage de trois vues déjà scopées, sans nouvelle requête ni nouveau scoping : les
     * séances viennent de {@code seanceService.findAll()} (filtré sur mon équipe par le
     * ScopeResolver, rôle JOUEUR), le GPS et les présences sont lus par {@code monJoueurId()}.
     * Réécrire une requête maison ici, c'est se rouvrir le risque de fuite entre joueurs.</p>
     *
     * <p>Fenêtre : 12 mois glissants. Sans borne, un club de plusieurs saisons enverrait des
     * centaines de séances à un téléphone pour n'en afficher que les dernières.</p>
     */
    @GetMapping("/historique-seances")
    public List<MaSeanceGpsDto> monHistoriqueSeances() {
        exigeModule(FeatureModule.GPS);
        UUID joueurId = monJoueurId();
        Map<UUID, GpsHistoriqueDto> gpsParSeance = joueurService.getHistoriqueGps(joueurId).stream()
                .collect(Collectors.toMap(GpsHistoriqueDto::seanceId, g -> g, (a, b) -> a));
        Map<UUID, String> statutParSeance = presenceService.mesDeclarations(joueurId).stream()
                .collect(Collectors.toMap(MaDeclaration::seanceId,
                        d -> d.statut() == null ? "PRESENT" : d.statut().name(), (a, b) -> a));

        LocalDate aujourdhui = horloge.today();
        LocalDate depuis = aujourdhui.minusMonths(12);
        return seanceService.findAll().stream()
                .filter(s -> s.getDate() != null
                        && !s.getDate().isAfter(aujourdhui) && !s.getDate().isBefore(depuis)
                        && !"ANNULEE".equals(s.getStatut()))
                .sorted(Comparator.comparing(Seance::getDate).reversed())
                .map(s -> new MaSeanceGpsDto(
                        s.getId(), s.getDate(),
                        s.getTypeSeance() != null ? s.getTypeSeance().getCode() : null,
                        s.getTypeSeance() != null ? s.getTypeSeance().getLibelle() : null,
                        // Présence par exception : aucune déclaration = présent.
                        statutParSeance.getOrDefault(s.getId(), "PRESENT"),
                        gpsParSeance.get(s.getId())))
                .toList();
    }

    @GetMapping("/pesees")
    public List<MaPeseeResponse> mesPesees() {
        exigeModule(FeatureModule.PESEES);
        return historiquePoidsRepository.findByJoueurIdOrderByDateDesc(monJoueurId()).stream()
                .map(h -> new MaPeseeResponse(h.getDate(), h.getPoids(), h.getCommentaire()))
                .toList();
    }

    @GetMapping("/blessures")
    public List<BlessureResponse> mesBlessures() {
        exigeModule(FeatureModule.MEDICAL);
        return blessureService.lister(monJoueurId());
    }

    /** Protocole de reprise (RTP) d'une de mes blessures — lecture seule. */
    @GetMapping("/blessures/{blessureId}/rtp")
    public List<EtapeResponse> mesEtapesRtp(@PathVariable UUID blessureId) {
        exigeModule(FeatureModule.MEDICAL);
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

    /**
     * Catalogue des types de séance avec les COULEURS DE SON CLUB. Le joueur n'a pas
     * {@code seances:read}, donc pas d'accès à {@code /api/type-seances} : sans cet endpoint,
     * son calendrier retomberait sur la palette par défaut et n'afficherait pas les couleurs
     * choisies par le club.
     */
    @GetMapping("/type-seances")
    public List<TypeSeanceResponse> mesTypeSeances() {
        return typeSeanceCatalogueService.catalogue();
    }

    /**
     * Contexte calendrier du joueur : les jours où SON ressenti est attendu (et s'il l'a rempli),
     * les séances qu'il a notées, et les anniversaires du club. Pendant de
     * {@code /api/calendrier/contexte}, mais du point de vue du joueur — les agrégats d'équipe
     * ne le concernent pas et ne doivent pas lui être exposés.
     */
    @GetMapping("/calendrier/contexte")
    public ContexteCalendrier monContexteCalendrier(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return calendrierContexteService.pourJoueur(monJoueurId(), debut, fin);
    }

    /**
     * Événements extrasportifs qui LE concernent : ceux de ses équipes ouverts aux joueurs, et,
     * s'ils ciblent des personnes précises, uniquement ceux où il figure.
     */
    @GetMapping("/evenements")
    public List<EvenementResponse> mesEvenements(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return evenementService.listerPourJoueur(monJoueurId(), debut, fin);
    }

    /** Contenu (exercices + schémas) d'une séance de l'équipe du joueur, lecture seule. */
    @GetMapping("/seances/{id}/exercices")
    public ContenuSeance mesSeanceExercices(@PathVariable UUID id) {
        seanceService.findById(id)
                .map(s -> { scopeResolver.verifieAcces(s.getEquipeId()); return s; })
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Séance introuvable"));
        return seanceService.getContenu(id);
    }

    /**
     * Fiche séance version joueur, FILTRÉE côté serveur : horaire, lieu, déroulé (blocs +
     * schémas) et SON groupe du jour — jamais les objectifs pédagogiques, dominantes,
     * projet de jeu ni l'affectation du staff.
     */
    @GetMapping("/seances/{id}/fiche")
    public FicheSeanceJoueur maFicheSeance(@PathVariable UUID id) {
        return seanceFicheService.ficheJoueur(id, monJoueurId());
    }

    /** Mes déclarations de présence déjà saisies (pour pré-remplir les boutons de la PWA). */
    @GetMapping("/presences")
    public List<MaDeclaration> mesDeclarations() {
        exigeModule(FeatureModule.PRESENCE);
        return presenceService.mesDeclarations(monJoueurId());
    }

    /**
     * Auto-déclaration de présence du joueur pour une séance de SON équipe (Présent / Absent +
     * commentaire). Met à jour la feuille d'appel du staff (source JOUEUR) ; une absence notifie le staff.
     */
    @PostMapping("/seances/{id}/presence")
    public LignePresence declarerPresence(@PathVariable UUID id, @RequestBody DeclarationPresence req) {
        exigeModule(FeatureModule.PRESENCE);
        seanceService.findById(id)
                .map(s -> { scopeResolver.verifieAcces(s.getEquipeId()); return s; })
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Séance introuvable"));
        return presenceService.declarerParJoueur(id, monJoueurId(), req);
    }

    // ──────────────────────────── Wellness (ressenti quotidien) ────────────────────────────

    @GetMapping("/wellness")
    public List<WellnessResponse> mesWellness() {
        exigeModule(FeatureModule.WELLNESS);
        return wellnessService.listerPourJoueur(monJoueurId());
    }

    /** Saisie/mise a jour du ressenti du jour (upsert sur joueur + date). */
    @PostMapping("/wellness")
    public WellnessResponse saisirWellness(@Valid @RequestBody WellnessRequest req) {
        exigeModule(FeatureModule.WELLNESS);
        return wellnessService.enregistrer(monJoueurId(), req);
    }

    // ──────────────────────────── RPE de seance ────────────────────────────

    @GetMapping("/rpe")
    public List<RpeResponse> mesRpe() {
        exigeModule(FeatureModule.WELLNESS);
        return rpeService.listerPourJoueur(monJoueurId());
    }

    /** Saisie/mise a jour du RPE d'une seance (upsert sur joueur + seance). */
    @PostMapping("/rpe")
    public RpeResponse saisirRpe(@Valid @RequestBody RpeRequest req) {
        exigeModule(FeatureModule.WELLNESS);
        return rpeService.enregistrer(monJoueurId(), req);
    }

    // ──────────────────────────── Conseils du staff (lecture) ────────────────────────────

    /** Conseils du staff visibles par le joueur : ceux de son equipe + ses conseils perso. */
    @GetMapping("/conseils")
    public List<ConseilResponse> mesConseils() {
        exigeModule(FeatureModule.WELLNESS);
        return conseilService.listerPourJoueur(monJoueurId());
    }

    // ──────────────────────────── Matchs partagés (lecture seule) ────────────────────────────

    /** Matchs publiés de l'équipe du joueur. */
    @GetMapping("/matchs")
    public List<MatchJoueurResume> mesMatchs() {
        exigeModule(FeatureModule.MATCH);
        return matchService.listerPourJoueur(monJoueurId());
    }

    /** Détail d'un match publié (consignes, ma consigne perso, mon statut, compo selon réglage staff). */
    @GetMapping("/matchs/{id}")
    public MatchJoueurDetail mesMatchDetail(@PathVariable UUID id) {
        exigeModule(FeatureModule.MATCH);
        return matchService.detailPourJoueur(monJoueurId(), id);
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
