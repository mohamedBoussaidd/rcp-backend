package com.remipreparateur.notification.service;

import com.remipreparateur.auth.entity.Utilisateur;
import com.remipreparateur.auth.repository.UtilisateurRepository;
import com.remipreparateur.club.entity.Equipe;
import com.remipreparateur.club.repository.EquipeRepository;
import com.remipreparateur.joueur.entity.Joueur;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.medical.wellness.repository.WellnessQuotidienRepository;
import com.remipreparateur.notification.entity.NotifConfigEquipe;
import com.remipreparateur.notification.entity.Priorite;
import com.remipreparateur.notification.entity.TypeNotification;
import com.remipreparateur.notification.repository.NotificationRepository;
import com.remipreparateur.performance.rpe.repository.RpeSeanceRepository;
import com.remipreparateur.performance.seance.entity.Seance;
import com.remipreparateur.performance.seance.repository.SeanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
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

    public NotificationScheduler(EquipeRepository equipeRepository, NotifConfigService configService,
                                 DigestService digestService, NotificationDispatcher dispatcher,
                                 JoueurRepository joueurRepository,
                                 WellnessQuotidienRepository wellnessRepository,
                                 SeanceRepository seanceRepository, RpeSeanceRepository rpeRepository,
                                 UtilisateurRepository utilisateurRepository,
                                 NotificationRepository notificationRepository) {
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
    }

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
