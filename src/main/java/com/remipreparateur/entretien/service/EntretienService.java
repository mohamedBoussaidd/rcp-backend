package com.remipreparateur.entretien.service;

import com.remipreparateur.auth.repository.UtilisateurRepository;
import com.remipreparateur.club.entity.Equipe;
import com.remipreparateur.club.repository.EquipeRepository;
import com.remipreparateur.entretien.dto.EntretienDtos.*;
import com.remipreparateur.entretien.entity.AutoEvaluation;
import com.remipreparateur.entretien.entity.AxeTravail;
import com.remipreparateur.entretien.entity.Entretien;
import com.remipreparateur.entretien.entity.EntretienAxe;
import com.remipreparateur.entretien.repository.AutoEvaluationRepository;
import com.remipreparateur.entretien.repository.AxeTravailRepository;
import com.remipreparateur.entretien.repository.EntretienAxeRepository;
import com.remipreparateur.entretien.repository.EntretienRepository;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.notification.service.NotificationProducer;
import com.remipreparateur.saison.entity.Saison;
import com.remipreparateur.saison.repository.EffectifSaisonRepository;
import com.remipreparateur.saison.repository.SaisonRepository;
import com.remipreparateur.saison.entity.EffectifSaison;
import com.remipreparateur.saison.service.AppartenanceService;
import com.remipreparateur.shared.security.CurrentUserProvider;
import com.remipreparateur.shared.security.ScopeResolver;
import com.remipreparateur.shared.time.Horloge;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Suivi individuel : axes de travail, entretiens et auto-évaluations. Rattaché à la fiche joueur.
 * Portée staff filtrée par équipe via {@link ScopeResolver} ; côté joueur, self-scope par joueurId
 * (le contrôleur {@code /api/moi} passe l'id du token). Dates métier via {@link Horloge}.
 */
@Service
public class EntretienService {

    private static final String STATUT_EN_COURS = "EN_COURS";
    private static final String STATUT_ABANDONNE = "ABANDONNE";
    private static final String VIS_PARTAGE = "PARTAGE_JOUEUR";
    private static final String VIS_STAFF = "STAFF";
    /** Cycle de vie d'un entretien : rendez-vous à venir → compte-rendu. Seul REALISE compte dans les agrégats. */
    private static final String ENT_PLANIFIE = "PLANIFIE";
    private static final String ENT_REALISE = "REALISE";

    private final AxeTravailRepository axeRepository;
    private final EntretienRepository entretienRepository;
    private final EntretienAxeRepository entretienAxeRepository;
    private final AutoEvaluationRepository autoEvalRepository;
    private final JoueurRepository joueurRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final EquipeRepository equipeRepository;
    private final SaisonRepository saisonRepository;
    private final EffectifSaisonRepository effectifRepository;
    private final ScopeResolver scopeResolver;
    private final CurrentUserProvider currentUser;
    private final Horloge horloge;
    private final NotificationProducer notifications;
    private final AppartenanceService appartenance;

    public EntretienService(AxeTravailRepository axeRepository, EntretienRepository entretienRepository,
                            EntretienAxeRepository entretienAxeRepository, AutoEvaluationRepository autoEvalRepository,
                            JoueurRepository joueurRepository, UtilisateurRepository utilisateurRepository,
                            EquipeRepository equipeRepository, SaisonRepository saisonRepository,
                            EffectifSaisonRepository effectifRepository, ScopeResolver scopeResolver,
                            CurrentUserProvider currentUser, Horloge horloge, NotificationProducer notifications,
                            AppartenanceService appartenance) {
        this.axeRepository = axeRepository;
        this.entretienRepository = entretienRepository;
        this.entretienAxeRepository = entretienAxeRepository;
        this.autoEvalRepository = autoEvalRepository;
        this.joueurRepository = joueurRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.equipeRepository = equipeRepository;
        this.saisonRepository = saisonRepository;
        this.effectifRepository = effectifRepository;
        this.scopeResolver = scopeResolver;
        this.currentUser = currentUser;
        this.horloge = horloge;
        this.notifications = notifications;
        this.appartenance = appartenance;
    }

    // ════════════════════════════ Axes (staff) ════════════════════════════

    @Transactional(readOnly = true)
    public List<AxeResponse> listerAxes(UUID joueurId) {
        Joueur joueur = joueurChecke(joueurId);
        Map<UUID, List<Eval>> parAxe = evalsParAxe(joueur.getId(), false);
        return axeRepository.findByJoueurIdOrderByCreatedAtDesc(joueur.getId()).stream()
                .map(a -> toAxeResponse(a, parAxe.getOrDefault(a.getId(), List.of())))
                .toList();
    }

    @Transactional
    public AxeResponse creerAxe(UUID joueurId, AxeRequest req) {
        Joueur joueur = joueurChecke(joueurId);
        AxeTravail a = new AxeTravail();
        a.setJoueurId(joueur.getId());
        a.setClubId(clubDeJoueur(joueur));
        a.setLibelle(req.libelle().trim());
        a.setCategorie(req.categorie());
        a.setStatut(req.statut() != null && !req.statut().isBlank() ? req.statut() : STATUT_EN_COURS);
        a.setCreePar(currentUser.current().getId());
        a.setUpdatedAt(LocalDateTime.now());
        return toAxeResponse(axeRepository.save(a), List.of());
    }

    @Transactional
    public AxeResponse modifierAxe(UUID id, AxeRequest req) {
        AxeTravail a = axeChecke(id);
        a.setLibelle(req.libelle().trim());
        a.setCategorie(req.categorie());
        if (req.statut() != null && !req.statut().isBlank()) {
            a.setStatut(req.statut());
        }
        a.setUpdatedAt(LocalDateTime.now());
        Map<UUID, List<Eval>> parAxe = evalsParAxe(a.getJoueurId(), false);
        return toAxeResponse(axeRepository.save(a), parAxe.getOrDefault(a.getId(), List.of()));
    }

    /** Suppression : hard delete si aucun entretien ne l'a évalué, sinon bascule en ABANDONNE. */
    @Transactional
    public void supprimerAxe(UUID id) {
        AxeTravail a = axeChecke(id);
        if (entretienAxeRepository.existsByAxeTravailId(a.getId())) {
            a.setStatut(STATUT_ABANDONNE);
            a.setUpdatedAt(LocalDateTime.now());
            axeRepository.save(a);
        } else {
            axeRepository.delete(a);
        }
    }

    // ════════════════════════════ Entretiens (staff) ════════════════════════════

    @Transactional(readOnly = true)
    public List<EntretienResponse> listerEntretiens(UUID joueurId, String type, LocalDate debut, LocalDate fin) {
        Joueur joueur = joueurChecke(joueurId);
        Map<UUID, AxeTravail> axes = axesParId(joueur.getId());
        return entretienRepository.findByJoueurIdOrderByDateEntretienDescCreatedAtDesc(joueur.getId()).stream()
                .filter(e -> type == null || type.isBlank() || type.equals(e.getType()))
                .filter(e -> debut == null || !e.getDateEntretien().isBefore(debut))
                .filter(e -> fin == null || !e.getDateEntretien().isAfter(fin))
                .map(e -> toEntretienResponse(e, axes))
                .toList();
    }

    @Transactional
    public EntretienResponse creerEntretien(EntretienRequest req) {
        Joueur joueur = joueurChecke(req.joueurId());
        String statut = statutDemande(req);
        controleDateStatut(statut, req.dateEntretien(), true);
        Entretien e = new Entretien();
        e.setJoueurId(joueur.getId());
        e.setClubId(clubDeJoueur(joueur));
        e.setEquipeId(appartenance.equipePrincipale(joueur.getId()));
        e.setType(req.type());
        e.setDateEntretien(req.dateEntretien());
        e.setHeure(req.heure());
        e.setStatut(statut);
        e.setMenePar(currentUser.current().getId());
        e.setNotes(videEnNull(req.notes()));
        // Un rendez-vous n'a pas de contenu à partager : la visibilité se joue au compte-rendu.
        e.setVisibilite(!ENT_PLANIFIE.equals(statut) && req.partager() ? VIS_PARTAGE : VIS_STAFF);
        e.setSeanceId(req.seanceId());
        e.setSchemaTactiqueId(req.schemaTactiqueId());
        e.setVideoUrl(videEnNull(req.videoUrl()));
        e.setUpdatedAt(LocalDateTime.now());
        Entretien saved = entretienRepository.save(e);

        appliquerLignes(saved, joueur, req.axes());
        if (ENT_PLANIFIE.equals(statut)) {
            notifications.entretienPlanifie(saved.getEquipeId(), saved.getJoueurId(),
                    libelleType(saved.getType()), saved.getDateEntretien(), saved.getHeure(), false);
        } else if (VIS_PARTAGE.equals(saved.getVisibilite())) {
            notifications.entretienPartage(saved.getEquipeId(), saved.getJoueurId());
        }
        return toEntretienResponse(saved, axesParId(joueur.getId()));
    }

    @Transactional
    public EntretienResponse modifierEntretien(UUID id, EntretienRequest req) {
        Entretien e = entretienChecke(id);
        exigeAuteurOuManage(e);
        Joueur joueur = joueurChecke(e.getJoueurId());
        String statut = statutDemande(req);
        boolean dateChangee = !req.dateEntretien().equals(e.getDateEntretien())
                || !java.util.Objects.equals(req.heure(), e.getHeure());
        // Garde de cohérence : n'exige date future que si la date bouge (un RDV en retard reste éditable).
        controleDateStatut(statut, req.dateEntretien(), dateChangee);
        boolean restePlanifie = ENT_PLANIFIE.equals(e.getStatut()) && ENT_PLANIFIE.equals(statut);
        e.setType(req.type());
        e.setDateEntretien(req.dateEntretien());
        e.setHeure(req.heure());
        e.setStatut(statut);
        e.setNotes(videEnNull(req.notes()));
        e.setSeanceId(req.seanceId());
        e.setSchemaTactiqueId(req.schemaTactiqueId());
        e.setVideoUrl(videEnNull(req.videoUrl()));
        e.setUpdatedAt(LocalDateTime.now());
        entretienRepository.save(e);
        // Remplace les lignes d'axes par le nouveau jeu fourni.
        entretienAxeRepository.deleteByEntretienId(e.getId());
        appliquerLignes(e, joueur, req.axes());
        // RDV déplacé (toujours PLANIFIE, date/heure modifiée) → re-notifie le joueur.
        if (restePlanifie && dateChangee) {
            notifications.entretienPlanifie(e.getEquipeId(), e.getJoueurId(),
                    libelleType(e.getType()), e.getDateEntretien(), e.getHeure(), true);
        }
        return toEntretienResponse(e, axesParId(joueur.getId()));
    }

    /** Statut demandé par la requête ; null/vide = REALISE (flux compte-rendu historique). */
    private String statutDemande(EntretienRequest req) {
        if (req.statut() == null || req.statut().isBlank()) return ENT_REALISE;
        if (!ENT_PLANIFIE.equals(req.statut()) && !ENT_REALISE.equals(req.statut())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Statut inconnu : " + req.statut());
        }
        return req.statut();
    }

    /**
     * Cohérence date/statut (dates métier via {@link Horloge}) : un compte-rendu ne se date pas dans
     * le futur ; un rendez-vous se planifie aujourd'hui ou plus tard ({@code exigeFutur} désarmé en
     * modification quand la date ne bouge pas, pour laisser un RDV en retard éditable).
     */
    private void controleDateStatut(String statut, LocalDate date, boolean exigeFutur) {
        LocalDate today = horloge.today();
        if (ENT_REALISE.equals(statut) && date.isAfter(today)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un compte-rendu ne peut pas être daté dans le futur — planifie plutôt un rendez-vous");
        }
        if (ENT_PLANIFIE.equals(statut) && exigeFutur && date.isBefore(today)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un rendez-vous se planifie aujourd'hui ou plus tard");
        }
    }

    private static String libelleType(String type) {
        return switch (type == null ? "" : type) {
            case "VIDEO" -> "vidéo";
            case "TERRAIN" -> "terrain";
            default -> "individuel";
        };
    }

    @Transactional
    public void supprimerEntretien(UUID id) {
        Entretien e = entretienChecke(id);
        exigeAuteurOuManage(e);
        entretienRepository.delete(e);   // entretien_axe supprimé en cascade (FK)
    }

    /** Bascule STAFF ↔ PARTAGE_JOUEUR. Au partage, notifie le joueur (si compte lié). */
    @Transactional
    public VisibiliteResponse basculerVisibilite(UUID id) {
        Entretien e = entretienChecke(id);
        exigeAuteurOuManage(e);
        if (ENT_PLANIFIE.equals(e.getStatut())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un rendez-vous n'a pas de contenu à partager — réalise d'abord l'entretien");
        }
        boolean versPartage = VIS_STAFF.equals(e.getVisibilite());
        e.setVisibilite(versPartage ? VIS_PARTAGE : VIS_STAFF);
        e.setUpdatedAt(LocalDateTime.now());
        entretienRepository.save(e);
        boolean notifEnvoyee = versPartage
                && notifications.entretienPartage(e.getEquipeId(), e.getJoueurId());
        return new VisibiliteResponse(e.getId(), e.getVisibilite(),
                VIS_PARTAGE.equals(e.getVisibilite()), notifEnvoyee);
    }

    // ════════════════════════════ Synthèse / vue équipe (staff) ════════════════════════════

    @Transactional(readOnly = true)
    public SyntheseResponse synthese(UUID joueurId) {
        Joueur joueur = joueurChecke(joueurId);
        Map<UUID, List<Eval>> parAxe = evalsParAxe(joueur.getId(), false);
        List<SyntheseAxe> axes = axeRepository.findByJoueurIdOrderByCreatedAtDesc(joueur.getId()).stream()
                .filter(a -> !STATUT_ABANDONNE.equals(a.getStatut()))
                .map(a -> {
                    List<Eval> evals = parAxe.getOrDefault(a.getId(), List.of());
                    List<SynthesePoint> serie = evals.stream()
                            .sorted(Comparator.comparing((Eval ev) -> ev.date))
                            .map(ev -> new SynthesePoint(ev.date, ev.note, ev.tendance))
                            .toList();
                    Optional<AutoEvaluation> auto = autoEvalRepository.findFirstByAxeTravailIdOrderByCreatedAtDesc(a.getId());
                    return new SyntheseAxe(a.getId(), a.getLibelle(), a.getCategorie(), a.getStatut(),
                            evals.size(), serie,
                            auto.map(AutoEvaluation::getNote).orElse(null),
                            auto.map(AutoEvaluation::getCreatedAt).orElse(null));
                })
                .toList();
        return new SyntheseResponse(joueur.getId(), axes);
    }

    @Transactional(readOnly = true)
    public List<EquipeLigne> vueEquipe() {
        UUID equipeId = scopeResolver.equipeActiveUnique();
        List<Joueur> effectif = effectifDeEquipe(equipeId);
        LocalDate today = horloge.today();
        Map<UUID, List<Entretien>> parJoueur = entretienRepository
                .findByJoueurIdInOrderByDateEntretienDescCreatedAtDesc(
                        effectif.stream().map(Joueur::getId).toList())
                .stream().collect(Collectors.groupingBy(Entretien::getJoueurId));

        return effectif.stream().map(j -> {
            // Agrégats sur les comptes-rendus REALISE ; les rendez-vous PLANIFIE alimentent prochainRdv.
            List<Entretien> tous = parJoueur.getOrDefault(j.getId(), List.of());
            List<Entretien> es = tous.stream().filter(e -> ENT_REALISE.equals(e.getStatut())).toList();
            LocalDate dernier = es.stream().map(Entretien::getDateEntretien)
                    .max(Comparator.naturalOrder()).orElse(null);
            LocalDate prochainRdv = tous.stream().filter(e -> ENT_PLANIFIE.equals(e.getStatut()))
                    .map(Entretien::getDateEntretien).min(Comparator.naturalOrder()).orElse(null);
            int nb30 = (int) es.stream().filter(e -> !e.getDateEntretien().isBefore(today.minusDays(30))).count();
            int nb90 = (int) es.stream().filter(e -> !e.getDateEntretien().isBefore(today.minusDays(90))).count();
            int video = (int) es.stream().filter(e -> "VIDEO".equals(e.getType())).count();
            int terrain = (int) es.stream().filter(e -> "TERRAIN".equals(e.getType())).count();
            int disc = (int) es.stream().filter(e -> "DISCUSSION".equals(e.getType())).count();
            return new EquipeLigne(j.getId(), j.getNom(), j.getPrenom(), j.getPostePrincipal(),
                    dernier, prochainRdv, nb30, nb90, video, terrain, disc);
        }).sorted(Comparator.comparing(EquipeLigne::dernierEntretien,
                Comparator.nullsFirst(Comparator.naturalOrder()))).toList();
    }

    // ════════════════════════════ Espace joueur (/api/moi) ════════════════════════════

    @Transactional(readOnly = true)
    public List<MonAxeResponse> mesAxes(UUID joueurId) {
        // Note staff visible au joueur UNIQUEMENT si issue d'un entretien PARTAGE_JOUEUR.
        Map<UUID, List<Eval>> parAxePartage = evalsParAxe(joueurId, true);
        return axeRepository.findByJoueurIdAndStatutOrderByCreatedAtDesc(joueurId, STATUT_EN_COURS).stream()
                .map(a -> {
                    Eval dern = derniereNote(parAxePartage.getOrDefault(a.getId(), List.of()));
                    Optional<AutoEvaluation> auto = autoEvalRepository.findFirstByAxeTravailIdOrderByCreatedAtDesc(a.getId());
                    return new MonAxeResponse(a.getId(), a.getLibelle(), a.getCategorie(), a.getStatut(),
                            dern != null ? dern.note : null,
                            dern != null ? dern.tendance : null,
                            auto.map(AutoEvaluation::getNote).orElse(null),
                            auto.map(AutoEvaluation::getCreatedAt).orElse(null));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MonEntretienResponse> mesEntretiens(UUID joueurId) {
        Map<UUID, AxeTravail> axes = axesParId(joueurId);
        return entretienRepository
                .findByJoueurIdAndVisibiliteOrderByDateEntretienDescCreatedAtDesc(joueurId, VIS_PARTAGE).stream()
                .filter(e -> ENT_REALISE.equals(e.getStatut()))   // les RDV à venir passent par l'agenda
                .map(e -> new MonEntretienResponse(e.getId(), e.getType(), e.getDateEntretien(),
                        e.getNotes(), e.getVideoUrl(), lignesResponse(e.getId(), axes)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AutoEvalResponse> mesAutoEvaluations(UUID joueurId) {
        return autoEvalRepository.findByJoueurIdOrderByCreatedAtDesc(joueurId).stream()
                .map(this::toAutoEvalResponse).toList();
    }

    /** Auto-évaluation du joueur sur un de SES axes EN_COURS. Max une par axe et par semaine. */
    @Transactional
    public AutoEvalResponse autoEvaluer(UUID joueurId, AutoEvalRequest req) {
        AxeTravail axe = axeRepository.findById(req.axeTravailId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Axe introuvable"));
        if (!axe.getJoueurId().equals(joueurId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cet axe n'est pas le vôtre");
        }
        if (!STATUT_EN_COURS.equals(axe.getStatut())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cet axe n'est plus en cours");
        }
        if (autoEvalRepository.existsByAxeTravailIdAndCreatedAtAfter(axe.getId(), LocalDateTime.now().minusDays(7))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tu t'es déjà auto-évalué sur cet axe cette semaine");
        }
        AutoEvaluation ae = new AutoEvaluation();
        ae.setJoueurId(joueurId);
        ae.setAxeTravailId(axe.getId());
        ae.setNote(req.note());
        ae.setCommentaire(videEnNull(req.commentaire()));
        return toAutoEvalResponse(autoEvalRepository.save(ae));
    }

    // ════════════════════════════ Scheduler (hors contexte HTTP) ════════════════════════════

    /**
     * Joueurs actifs de l'effectif d'une équipe dont le dernier entretien dépasse le seuil (ou qui
     * n'en ont jamais eu). Appelé par le {@code NotificationScheduler} : n'utilise NI ScopeResolver
     * NI Horloge (heure réelle {@link LocalDate#now()}), car il n'y a pas de requête HTTP.
     */
    @Transactional(readOnly = true)
    public List<Joueur> joueursSansEntretienRecent(UUID equipeId, int seuilJours) {
        LocalDate limite = LocalDate.now().minusDays(seuilJours);
        List<Joueur> effectif = effectifDeEquipe(equipeId).stream()
                .filter(j -> !"inactif".equalsIgnoreCase(j.getStatut()))
                .toList();
        if (effectif.isEmpty()) return List.of();
        Map<UUID, List<Entretien>> parJoueur = entretienRepository
                .findByJoueurIdInOrderByDateEntretienDescCreatedAtDesc(effectif.stream().map(Joueur::getId).toList())
                .stream().collect(Collectors.groupingBy(Entretien::getJoueurId));
        return effectif.stream().filter(j -> {
            // Seuls les comptes-rendus REALISE comptent : un RDV planifié ne « réarme » pas l'alerte.
            LocalDate dernier = parJoueur.getOrDefault(j.getId(), List.of()).stream()
                    .filter(e -> ENT_REALISE.equals(e.getStatut()))
                    .map(Entretien::getDateEntretien).max(Comparator.naturalOrder()).orElse(null);
            return dernier == null || dernier.isBefore(limite);
        }).toList();
    }

    // ════════════════════════════ Agenda (calendrier) ════════════════════════════

    /**
     * Entretiens d'une période pour le calendrier staff (RDV planifiés + comptes-rendus), portée
     * équipe via {@link ScopeResolver}. Hybride « voyage dans la saison » : en date simulée, on
     * masque le futur (même règle que {@code SeanceService.findByPeriode}).
     */
    @Transactional(readOnly = true)
    public List<AgendaEntretien> agendaEquipe(LocalDate debut, LocalDate fin) {
        if (horloge.estSimulee() && fin != null && fin.isAfter(horloge.today())) fin = horloge.today();
        if (debut == null || fin == null || fin.isBefore(debut)) return List.of();
        var scope = scopeResolver.resolve();
        List<Entretien> entretiens;
        if (scope.all()) {
            entretiens = entretienRepository.findByDateEntretienBetweenOrderByDateEntretienAscHeureAsc(debut, fin);
        } else if (scope.none()) {
            return List.of();
        } else {
            entretiens = entretienRepository
                    .findByDateEntretienBetweenAndEquipeIdInOrderByDateEntretienAscHeureAsc(debut, fin, scope.equipeIds());
        }
        return toAgenda(entretiens);
    }

    /** Agenda PWA joueur : SES rendez-vous PLANIFIE de la période — type/date/heure, jamais les notes. */
    @Transactional(readOnly = true)
    public List<AgendaEntretien> agendaJoueur(UUID joueurId, LocalDate debut, LocalDate fin) {
        if (horloge.estSimulee() && fin != null && fin.isAfter(horloge.today())) fin = horloge.today();
        if (debut == null || fin == null || fin.isBefore(debut)) return List.of();
        return toAgenda(entretienRepository
                .findByJoueurIdAndStatutAndDateEntretienBetweenOrderByDateEntretienAscHeureAsc(
                        joueurId, ENT_PLANIFIE, debut, fin));
    }

    private List<AgendaEntretien> toAgenda(List<Entretien> entretiens) {
        if (entretiens.isEmpty()) return List.of();
        Map<UUID, Joueur> joueurs = joueurRepository
                .findAllById(entretiens.stream().map(Entretien::getJoueurId).distinct().toList())
                .stream().collect(Collectors.toMap(Joueur::getId, Function.identity()));
        return entretiens.stream().map(e -> {
            Joueur j = joueurs.get(e.getJoueurId());
            return new AgendaEntretien(e.getId(), e.getJoueurId(),
                    j != null ? j.getNom() : null, j != null ? j.getPrenom() : null,
                    e.getType(), e.getDateEntretien(), e.getHeure(), e.getStatut());
        }).toList();
    }

    // ════════════════════════════ Interne ════════════════════════════

    /** Applique les lignes d'axes d'un entretien : axe existant OU création à la volée. */
    private void appliquerLignes(Entretien e, Joueur joueur, List<LigneAxeRequest> lignes) {
        if (lignes == null) return;
        for (LigneAxeRequest l : lignes) {
            UUID axeId = l.axeTravailId();
            if (axeId == null) {
                if (l.nouvelAxeLibelle() == null || l.nouvelAxeLibelle().isBlank()) continue;
                AxeTravail a = new AxeTravail();
                a.setJoueurId(joueur.getId());
                a.setClubId(clubDeJoueur(joueur));
                a.setLibelle(l.nouvelAxeLibelle().trim());
                a.setCategorie(l.nouvelAxeCategorie() != null ? l.nouvelAxeCategorie() : "TECHNIQUE");
                a.setStatut(STATUT_EN_COURS);
                a.setCreePar(currentUser.current().getId());
                a.setUpdatedAt(LocalDateTime.now());
                axeId = axeRepository.save(a).getId();
            } else {
                AxeTravail a = axeRepository.findById(axeId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Axe introuvable"));
                if (!a.getJoueurId().equals(joueur.getId())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Axe d'un autre joueur");
                }
            }
            EntretienAxe ea = new EntretienAxe();
            ea.setEntretienId(e.getId());
            ea.setAxeTravailId(axeId);
            ea.setNote(l.note());
            ea.setTendance(videEnNull(l.tendance()));
            ea.setCommentaire(videEnNull(l.commentaire()));
            entretienAxeRepository.save(ea);
        }
    }

    /** Évaluations (date + note + tendance) groupées par axe pour un joueur. REALISE uniquement :
     *  un rendez-vous à venir ne doit alimenter ni les courbes ni les agrégats. */
    private Map<UUID, List<Eval>> evalsParAxe(UUID joueurId, boolean partageSeulement) {
        List<Entretien> entretiens = entretienRepository
                .findByJoueurIdOrderByDateEntretienDescCreatedAtDesc(joueurId).stream()
                .filter(e -> ENT_REALISE.equals(e.getStatut()))
                .filter(e -> !partageSeulement || VIS_PARTAGE.equals(e.getVisibilite()))
                .toList();
        if (entretiens.isEmpty()) return Map.of();
        Map<UUID, Entretien> parId = entretiens.stream()
                .collect(Collectors.toMap(Entretien::getId, Function.identity()));
        Map<UUID, List<Eval>> parAxe = new java.util.HashMap<>();
        for (EntretienAxe ea : entretienAxeRepository.findByEntretienIdIn(new ArrayList<>(parId.keySet()))) {
            Entretien e = parId.get(ea.getEntretienId());
            if (e == null) continue;
            parAxe.computeIfAbsent(ea.getAxeTravailId(), k -> new ArrayList<>())
                    .add(new Eval(e.getDateEntretien(), ea.getNote(), ea.getTendance()));
        }
        return parAxe;
    }

    /** Dernière évaluation NOTÉE (par date desc) parmi une liste. */
    private Eval derniereNote(List<Eval> evals) {
        return evals.stream()
                .filter(ev -> ev.note != null)
                .max(Comparator.comparing(ev -> ev.date))
                .orElse(null);
    }

    private AxeResponse toAxeResponse(AxeTravail a, List<Eval> evals) {
        Eval dern = derniereNote(evals);
        Integer autoNote = autoEvalRepository.findFirstByAxeTravailIdOrderByCreatedAtDesc(a.getId())
                .map(AutoEvaluation::getNote).orElse(null);
        return new AxeResponse(a.getId(), a.getJoueurId(), a.getLibelle(), a.getCategorie(), a.getStatut(),
                evals.size(),
                dern != null ? dern.note : null,
                dern != null ? dern.tendance : null,
                autoNote, a.getCreatedAt(), a.getUpdatedAt());
    }

    private EntretienResponse toEntretienResponse(Entretien e, Map<UUID, AxeTravail> axes) {
        String nom = null;
        if (e.getMenePar() != null) {
            nom = utilisateurRepository.findById(e.getMenePar())
                    .map(u -> ((orVide(u.getPrenom()) + " " + orVide(u.getNom())).trim()))
                    .filter(s -> !s.isBlank()).orElse(null);
        }
        return new EntretienResponse(e.getId(), e.getJoueurId(), e.getType(), e.getDateEntretien(),
                e.getHeure(), e.getStatut(), e.getMenePar(), nom, e.getNotes(), e.getVisibilite(),
                VIS_PARTAGE.equals(e.getVisibilite()), e.getSeanceId(), e.getSchemaTactiqueId(),
                e.getVideoUrl(), lignesResponse(e.getId(), axes), e.getCreatedAt(), e.getUpdatedAt());
    }

    private List<LigneAxeResponse> lignesResponse(UUID entretienId, Map<UUID, AxeTravail> axes) {
        return entretienAxeRepository.findByEntretienId(entretienId).stream()
                .map(ea -> {
                    AxeTravail a = axes.get(ea.getAxeTravailId());
                    return new LigneAxeResponse(ea.getId(), ea.getAxeTravailId(),
                            a != null ? a.getLibelle() : null,
                            a != null ? a.getCategorie() : null,
                            ea.getNote(), ea.getTendance(), ea.getCommentaire());
                })
                .toList();
    }

    private AutoEvalResponse toAutoEvalResponse(AutoEvaluation ae) {
        return new AutoEvalResponse(ae.getId(), ae.getAxeTravailId(), ae.getNote(),
                ae.getCommentaire(), ae.getCreatedAt());
    }

    private Map<UUID, AxeTravail> axesParId(UUID joueurId) {
        return axeRepository.findByJoueurIdOrderByCreatedAtDesc(joueurId).stream()
                .collect(Collectors.toMap(AxeTravail::getId, Function.identity()));
    }

    /** Effectif de la saison active de l'équipe ; repli sur l'effectif global (pré-pivot). */
    private List<Joueur> effectifDeEquipe(UUID equipeId) {
        Equipe equipe = equipeRepository.findById(equipeId).orElse(null);
        if (equipe != null) {
            Saison saison = saisonRepository.findFirstByClubIdAndStatut(equipe.getClubId(), "EN_COURS").orElse(null);
            if (saison != null) {
                List<UUID> ids = effectifRepository.findBySaisonIdAndEquipeId(saison.getId(), equipeId)
                        .stream().map(EffectifSaison::getJoueurId).toList();
                if (!ids.isEmpty()) return joueurRepository.findAllById(ids);
            }
        }
        return List.of();   // Phase 4 : plus de repli sur le cache joueur.equipe_id (aucun effectif = vide)
    }

    private Joueur joueurChecke(UUID joueurId) {
        Joueur joueur = joueurRepository.findById(joueurId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Joueur introuvable"));
        scopeResolver.verifieAccesPersonne(joueur.getId(), joueur.getClubId());
        return joueur;
    }

    private AxeTravail axeChecke(UUID id) {
        AxeTravail a = axeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Axe introuvable"));
        joueurChecke(a.getJoueurId());
        return a;
    }

    private Entretien entretienChecke(UUID id) {
        Entretien e = entretienRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entretien introuvable"));
        scopeResolver.verifieAcces(e.getEquipeId());
        return e;
    }

    /** Auteur de l'entretien, ou détenteur de entretien:manage (modération). Sinon 403. */
    private void exigeAuteurOuManage(Entretien e) {
        UUID moi = currentUser.current().getId();
        if (moi.equals(e.getMenePar())) return;
        boolean manage = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> "entretien:manage".equals(a.getAuthority()));
        if (!manage) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Seul l'auteur (ou un modérateur) peut modifier cet entretien");
        }
    }

    private UUID clubDeJoueur(Joueur joueur) {
        // Phase 4 : le club est porté directement par la fiche (plus de dérivation via l'équipe).
        return joueur.getClubId() != null ? joueur.getClubId() : scopeResolver.clubActif();
    }

    private static String orVide(String s) { return s == null ? "" : s; }

    private String videEnNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /** Évaluation d'un axe à une date (usage interne synthèse/agrégats). */
    private record Eval(LocalDate date, Integer note, String tendance) {}
}
