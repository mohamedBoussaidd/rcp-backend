package com.remipreparateur.contrat.service;

import com.remipreparateur.club.repository.EquipeRepository;
import com.remipreparateur.contrat.entity.Contrat;
import com.remipreparateur.contrat.repository.ContratRepository;
import com.remipreparateur.joueur.repository.JoueurRepository;
import com.remipreparateur.notification.service.NotificationProducer;
import com.remipreparateur.saison.service.AppartenanceService;
import com.remipreparateur.shared.time.Horloge;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Échéances de contrat : chaque matin, notifie à J-90 et J-30 exactement (une seule fois
 * par jalon, puisque déclenché sur la date pile) le Président/Administratif du club ET la
 * personne concernée. Utilise {@link Horloge} (compatible date simulée en lecture).
 */
@Service
public class ContratEcheanceScheduler {

    private static final List<Long> JALONS_JOURS = List.of(90L, 30L);

    private final ContratRepository contratRepository;
    private final JoueurRepository joueurRepository;
    private final EquipeRepository equipeRepository;
    private final AppartenanceService appartenance;
    private final NotificationProducer notifications;
    private final Horloge horloge;

    public ContratEcheanceScheduler(ContratRepository contratRepository, JoueurRepository joueurRepository,
                                    EquipeRepository equipeRepository, AppartenanceService appartenance,
                                    NotificationProducer notifications, Horloge horloge) {
        this.contratRepository = contratRepository;
        this.joueurRepository = joueurRepository;
        this.equipeRepository = equipeRepository;
        this.appartenance = appartenance;
        this.notifications = notifications;
        this.horloge = horloge;
    }

    @Scheduled(cron = "0 40 7 * * *")
    public void verifierEcheances() {
        LocalDate today = horloge.today();
        for (long jours : JALONS_JOURS) {
            for (Contrat c : contratRepository.findByDateFin(today.plusDays(jours))) {
                notifier(c, jours);
            }
        }
    }

    private void notifier(Contrat c, long joursRestants) {
        String nom = joueurRepository.findById(c.getJoueurId())
                .map(j -> (j.getPrenom() != null ? j.getPrenom() + " " : "") + j.getNom())
                .orElse("un membre du club");
        // Ancre d'équipe requise par le fan-out club-wide : n'importe quelle équipe du club.
        UUID equipeAncre = equipeRepository.findByClubId(c.getClubId()).stream()
                .findFirst().map(e -> e.getId()).orElse(null);
        if (equipeAncre != null) {
            notifications.contratEcheanceStaff(c.getClubId(), equipeAncre, nom, c.getDateFin(), joursRestants);
        }
        notifications.contratEcheancePersonne(appartenance.equipePrincipale(c.getJoueurId()),
                c.getJoueurId(), c.getDateFin(), joursRestants);
    }
}
