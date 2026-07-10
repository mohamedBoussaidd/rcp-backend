package com.remipreparateur.notification.service;

import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.auth.repository.UtilisateurRepository;
import com.remipreparateur.auth.rbac.FeatureModule;
import com.remipreparateur.club.entity.Club;
import com.remipreparateur.club.entity.Equipe;
import com.remipreparateur.club.pack.ClubModulesService;
import com.remipreparateur.club.repository.ClubRepository;
import com.remipreparateur.club.repository.EquipeRepository;
import com.remipreparateur.auth.entity.Role;
import com.remipreparateur.documentadmin.entity.DocumentJoueur;
import com.remipreparateur.documentadmin.entity.TypeDocumentRequis;
import com.remipreparateur.documentadmin.repository.TypeDocumentRequisRepository;
import com.remipreparateur.documentadmin.service.DocumentAdminService;
import com.remipreparateur.entretien.service.EntretienService;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.medical.blessure.entity.Blessure;
import com.remipreparateur.medical.blessure.service.BlessureService;
import com.remipreparateur.medical.wellness.repository.WellnessQuotidienRepository;
import com.remipreparateur.notification.entity.NotifConfigEquipe;
import com.remipreparateur.notification.entity.Priorite;
import com.remipreparateur.notification.entity.TypeNotification;
import com.remipreparateur.notification.repository.NotificationRepository;
import com.remipreparateur.performance.rpe.repository.RpeSeanceRepository;
import com.remipreparateur.performance.seance.entity.Seance;
import com.remipreparateur.performance.seance.repository.SeanceRepository;
import com.remipreparateur.saison.service.AppartenanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Déclencheur temporel des notifications : tourne chaque minute (hors session HTTP) et, pour
 * chaque équipe, émet selon sa config — rappels joueur (wellness, séance, RPE) aux heures
 * paramétrées et digests « à surveiller » matin/soir. Les émissions sont dédoublonnées par jour.
 */
@Component
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);

    private final EquipeRepository equipeRepository;
    private final NotifConfigService configService;
    private final DigestService digestService;
    private final NotificationDispatcher dispatcher;
    private final JoueurRepository joueurRepository;
    private final WellnessQuotidienRepository wellnessRepository;
    private final SeanceRepository seanceRepository;
    private final RpeSeanceRepository rpeRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final NotificationRepository notificationRepository;
    private final BlessureService blessureService;
    private final EntretienService entretienService;
    private final ClubRepository clubRepository;
    private final ClubModulesService clubModulesService;
    private final DocumentAdminService documentAdminService;
    private final TypeDocumentRequisRepository typeDocumentRequisRepository;
    private final NotificationProducer notifications;
    private final AppartenanceService appartenance;

    public NotificationScheduler(EquipeRepository equipeRepository, NotifConfigService configService,
                                 DigestService digestService, NotificationDispatcher dispatcher,
                                 JoueurRepository joueurRepository,
                                 WellnessQuotidienRepository wellnessRepository,
                                 SeanceRepository seanceRepository, RpeSeanceRepository rpeRepository,
                                 UtilisateurRepository utilisateurRepository,
                                 NotificationRepository notificationRepository,
                                 BlessureService blessureService,
                                 EntretienService entretienService,
                                 ClubRepository clubRepository,
                                 ClubModulesService clubModulesService,
                                 DocumentAdminService documentAdminService,
                                 TypeDocumentRequisRepository typeDocumentRequisRepository,
                                 NotificationProducer notifications,
                                 AppartenanceService appartenance) {
        this.equipeRepository = equipeRepository;
        this.configService = configService;
        this.digestService = digestService;
        this.dispatcher = dispatcher;
        this.joueurRepository = joueurRepository;
        this.wellnessRepository = wellnessRepository;
        this.seanceRepository = seanceRepository;
        this.rpeRepository = rpeRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.notificationRepository = notificationRepository;
        this.blessureService = blessureService;
        this.entretienService = entretienService;
        this.clubRepository = clubRepository;
        this.clubModulesService = clubModulesService;
        this.documentAdminService = documentAdminService;
        this.typeDocumentRequisRepository = typeDocumentRequisRepository;
        this.notifications = notifications;
        this.appartenance = appartenance;
    }

    /**
     * Quotidien (07:30) : documents administratifs VALIDE dont l'expiration est dépassée →
     * EXPIRE, notifie le joueur concerné. Heure réelle (jamais l'Horloge simulée), cf.
     * {@link DocumentAdminService#expirerDepasses()}.
     */
    @Scheduled(cron = "0 30 7 * * *")
    public void expirerDocumentsAdmin() {
        try {
            for (DocumentJoueur d : documentAdminService.expirerDepasses()) {
                Joueur j = joueurRepository.findById(d.getJoueurId()).orElse(null);
                if (j == null) continue;
                UUID eq = appartenance.equipePrincipale(j.getId());
                if (eq == null) continue;
                TypeDocumentRequis type = typeDocumentRequisRepository.findById(d.getTypeDocumentRequisId()).orElse(null);
                notifications.documentAdminExpire(eq, j.getId(),
                        type != null ? type.getLibelle() : "Document");
            }
        } catch (Exception e) {
            log.warn("Expiration documents administratifs : {}", e.getMessage());
        }
    }

    /**
     * Hebdomadaire (lundi 08:15) : relance chaque joueur incomplet (document manquant/refusé) +
     * UN digest de conformité par club aux Président/Administratif. Boucle par CLUB (pas par
     * équipe comme {@link #tick}) : ce domaine est piloté au niveau club, pas équipe.
     */
    @Scheduled(cron = "0 15 8 * * MON")
    public void relanceEtDigestDocumentsAdmin() {
        for (Club club : clubRepository.findAll()) {
            try {
                traiterDocumentsAdminClub(club.getId());
            } catch (Exception e) {
                log.warn("Relance/digest documents administratifs — club {} : {}", club.getId(), e.getMessage());
            }
        }
    }

    private void traiterDocumentsAdminClub(UUID clubId) {
        if (!clubModulesService.modulesActifs(clubId).contains(FeatureModule.DOCUMENTS_ADMIN.getCode())) return;
        for (Map.Entry<Joueur, Integer> entry : documentAdminService.joueursIncomplets(clubId).entrySet()) {
            Joueur j = entry.getKey();
            UUID eq = appartenance.equipePrincipale(j.getId());
            if (eq == null) continue;
            notifications.relanceDocumentsAdmin(eq, j.getId(), entry.getValue());
        }
        List<Equipe> equipes = equipeRepository.findByClubId(clubId);
        if (equipes.isEmpty()) return; // pas d'équipe = pas d'ancrage possible pour le digest club-wide
        UUID ancre = equipes.get(0).getId();
        DocumentAdminService.ResumeConformiteClub resume = documentAdminService.resumeConformite(clubId);
        notifications.digestConformiteDocuments(clubId, ancre,
                resume.incomplets(), resume.aValider(), resume.expirentSous30j());
    }

    /**
     * Réconciliation quotidienne des blessures (08:00) : solde celles dont la date de retour
     * prévue est dépassée (joueur de nouveau disponible) et notifie le staff médical +
     * préparateur pour confirmation/prolongation. Cf. {@link BlessureService#cloturerRetoursDepasses()}.
     */
    @Scheduled(cron = "0 0 8 * * *")
    public void reconcilierRetoursBlessure() {
        try {
            for (Blessure b : blessureService.cloturerRetoursDepasses()) {
                if (b.getEquipeId() == null) continue;
                Joueur j = joueurRepository.findById(b.getJoueurId()).orElse(null);
                String nom = j != null ? (orVide(j.getPrenom()) + " " + orVide(j.getNom())).trim() : "Un joueur";
                if (nom.isBlank()) nom = "Un joueur";
                dispatcher.versStaffRoles(b.getEquipeId(),
                        List.of(Role.MEDICAL, Role.PREPARATEUR),
                        TypeNotification.RETOUR_BLESSURE_A_CONFIRMER,
                        "Retour à confirmer : " + nom,
                        "La date de retour prévue de " + nom + " est atteinte. Le joueur est de nouveau "
                                + "disponible — confirme son retour ou prolonge l'indisponibilité.",
                        "/medical",
                        Priorite.NORMALE);
            }
        } catch (Exception e) {
            log.warn("Réconciliation retours blessure : {}", e.getMessage());
        }
    }

    private static String orVide(String s) { return s == null ? "" : s; }

    /** Tick minute : évalue chaque équipe. Cron à la seconde 0 de chaque minute. */
    @Scheduled(cron = "0 * * * * *")
    public void tick() {
        LocalTime now = LocalTime.now().withSecond(0).withNano(0);
        for (Equipe equipe : equipeRepository.findAll()) {
            try {
                traiterEquipe(equipe.getId(), now);
            } catch (Exception e) {
                log.warn("Notification scheduler — équipe {} : {}", equipe.getId(), e.getMessage());
            }
        }
    }

    /** Heure du rappel staff « vérifier la semaine à venir » (dimanche). */
    private static final LocalTime HEURE_VERIF_SEMAINE = LocalTime.of(18, 0);

    private void traiterEquipe(UUID equipeId, LocalTime now) {
        NotifConfigEquipe cfg = configService.getOrCreate(equipeId);

        if (cfg.isRappelWellnessActif() && estLHeure(now, cfg.getRappelWellnessHeure())) {
            rappelWellness(equipeId);
            if (cfg.isRappelSeanceActif()) rappelSeance(equipeId);
        }
        if (cfg.isRappelRpeActif()) {
            rappelRpe(equipeId, cfg.getRappelRpeDelaiHeures());
        }
        if (cfg.isDigestActif() && estLHeure(now, cfg.getDigestMatinHeure())) {
            digestService.genererEtEnvoyer(cfg, "matin");
        }
        if (cfg.isDigestActif() && estLHeure(now, cfg.getDigestSoirHeure())) {
            digestService.genererEtEnvoyer(cfg, "soir");
        }
        // Rappel staff : dimanche en fin de journée, vérifier la semaine d'entraînement à venir.
        if (LocalDate.now().getDayOfWeek() == java.time.DayOfWeek.SUNDAY
                && estLHeure(now, HEURE_VERIF_SEMAINE)) {
            verifSemaine(equipeId);
        }
        // Alerte staff : lundi matin, digest des joueurs sans entretien récent.
        if (LocalDate.now().getDayOfWeek() == java.time.DayOfWeek.MONDAY
                && estLHeure(now, HEURE_ALERTE_ENTRETIEN)) {
            alerteEntretiens(equipeId, cfg);
        }
    }

    /** Heure du digest staff « joueurs sans entretien récent » (lundi matin). */
    private static final LocalTime HEURE_ALERTE_ENTRETIEN = LocalTime.of(8, 0);

    /**
     * Digest hebdomadaire (lundi) : liste les joueurs de l'effectif sans entretien depuis plus du
     * seuil configuré par l'équipe. Une seule notification par équipe, aux Entraîneur / Préparateur /
     * Président. Anti-doublon : rien de neuf tant qu'une alerte a été émise il y a moins de 3 semaines.
     */
    private void alerteEntretiens(UUID equipeId, NotifConfigEquipe cfg) {
        if (!cfg.isEntretienAlerteActive()) return;
        if (notificationRepository.existsByEquipeIdAndTypeAndCreatedAtAfter(
                equipeId, TypeNotification.ALERTE_ENTRETIEN, LocalDateTime.now().minusWeeks(3))) {
            return;
        }
        List<Joueur> sans = entretienService.joueursSansEntretienRecent(equipeId, cfg.getEntretienSeuilJours());
        if (sans.isEmpty()) return;

        int semaines = Math.round(cfg.getEntretienSeuilJours() / 7f);
        String noms = sans.stream().limit(8)
                .map(j -> (orVide(j.getPrenom()) + " " + orVide(j.getNom())).trim())
                .filter(s -> !s.isBlank())
                .collect(java.util.stream.Collectors.joining(", "));
        boolean pluriel = sans.size() > 1;
        String titre = sans.size() + (pluriel ? " joueurs sans entretien récent" : " joueur sans entretien récent");
        String corps = sans.size() + (pluriel ? " joueurs n'ont" : " joueur n'a")
                + " pas eu d'entretien depuis plus de " + semaines + " semaines : " + noms
                + (sans.size() > 8 ? "…" : ".");

        dispatcher.versStaffRoles(equipeId,
                List.of(Role.ENTRAINEUR, Role.PREPARATEUR, Role.PRESIDENT),
                TypeNotification.ALERTE_ENTRETIEN, titre, corps, "/suivi-entretiens", Priorite.NORMALE);
    }

    /**
     * Notifie le préparateur ET l'entraîneur de l'équipe pour vérifier la semaine à venir
     * (lundi → dimanche prochains). Deux cas : des séances sont posées (les vérifier) ou
     * aucune (en poser). Dédoublonné par équipe sur la journée.
     */
    private void verifSemaine(UUID equipeId) {
        LocalDateTime debutJour = LocalDate.now().atStartOfDay();
        if (notificationRepository
                .existsByEquipeIdAndTypeAndCreatedAtAfter(equipeId, TypeNotification.VERIF_SEMAINE, debutJour)) {
            return;
        }
        LocalDate lundiProchain = LocalDate.now().plusDays(1);            // demain = lundi
        LocalDate dimancheProchain = lundiProchain.plusDays(6);
        boolean aSeances = !seanceRepository
                .findByDateBetweenAndEquipeIdInOrderByDateAscHeureDebutAsc(
                        lundiProchain, dimancheProchain, List.of(equipeId)).isEmpty();

        String titre = aSeances ? "Vérifie la semaine à venir" : "Aucune séance posée";
        String corps = aSeances
                ? "Contrôle les séances de la semaine prochaine (types, durées, horaires) avant qu'elle ne démarre."
                : "Aucune séance n'est posée pour la semaine prochaine. Pense à planifier l'entraînement.";

        dispatcher.versStaffRoles(equipeId,
                List.of(com.remipreparateur.auth.entity.Role.PREPARATEUR,
                        com.remipreparateur.auth.entity.Role.ENTRAINEUR),
                TypeNotification.VERIF_SEMAINE, titre, corps, "/planning-technique",
                Priorite.NORMALE);
    }

    // ── Rappels joueur ──

    private void rappelWellness(UUID equipeId) {
        LocalDate today = LocalDate.now();
        for (Joueur j : joueursActifs(equipeId)) {
            if (wellnessRepository.findByJoueurIdAndDate(j.getId(), today).isPresent()) continue;
            if (dejaEmisAujourdhui(j.getId(), TypeNotification.RAPPEL_WELLNESS)) continue;
            dispatcher.versJoueurFiche(equipeId, j.getId(), TypeNotification.RAPPEL_WELLNESS,
                    "Wellness du jour", "Pense à renseigner ton ressenti du matin.",
                    "/joueur", Priorite.NORMALE, null, null, false);
        }
    }

    private void rappelSeance(UUID equipeId) {
        boolean seanceAujourdhui = !seanceRepository
                .findByDateBetweenAndEquipeIdInOrderByDateAscHeureDebutAsc(
                        LocalDate.now(), LocalDate.now(), List.of(equipeId)).isEmpty();
        if (!seanceAujourdhui) return;
        dispatcher.versEquipeJoueurs(equipeId, TypeNotification.RAPPEL_SEANCE,
                "Séance aujourd'hui", "Tu as une séance prévue aujourd'hui.", "/joueur",
                null, null, false);
    }

    private void rappelRpe(UUID equipeId, short delaiHeures) {
        LocalDate today = LocalDate.now();
        List<Seance> seances = seanceRepository
                .findByDateBetweenAndEquipeIdInOrderByDateAscHeureDebutAsc(today, today, List.of(equipeId));
        LocalTime now = LocalTime.now();
        boolean uneSeanceTerminee = seances.stream().anyMatch(s -> {
            LocalTime ref = s.getHeureFin() != null ? s.getHeureFin() : s.getHeureDebut();
            return ref != null && now.isAfter(ref.plusHours(delaiHeures));
        });
        if (!uneSeanceTerminee) return;
        for (Seance s : seances) {
            for (Joueur j : joueursActifs(equipeId)) {
                if (rpeRepository.findByJoueurIdAndSeanceId(j.getId(), s.getId()).isPresent()) continue;
                if (dejaEmisAujourdhui(j.getId(), TypeNotification.RAPPEL_RPE)) continue;
                dispatcher.versJoueurFiche(equipeId, j.getId(), TypeNotification.RAPPEL_RPE,
                        "RPE à saisir", "Renseigne ton effort ressenti pour la séance d'aujourd'hui.",
                        "/joueur", Priorite.NORMALE, null, null, false);
            }
        }
    }

    // ── Helpers ──

    private List<Joueur> joueursActifs(UUID equipeId) {
        return joueurRepository.findByEquipeIdIn(List.of(equipeId)).stream()
                .filter(j -> !"inactif".equalsIgnoreCase(j.getStatut()))
                .toList();
    }

    /** Une notif de ce type a-t-elle déjà été émise au compte du joueur aujourd'hui ? */
    private boolean dejaEmisAujourdhui(UUID joueurId, TypeNotification type) {
        Utilisateur u = utilisateurRepository.findByJoueurId(joueurId).orElse(null);
        if (u == null) return true; // pas de compte → rien à envoyer
        LocalDateTime debutJour = LocalDate.now().atStartOfDay();
        return notificationRepository
                .existsByDestinataireUserIdAndTypeAndCreatedAtAfter(u.getId(), type, debutJour);
    }

    private boolean estLHeure(LocalTime now, LocalTime cible) {
        return cible != null && now.getHour() == cible.getHour() && now.getMinute() == cible.getMinute();
    }
}
