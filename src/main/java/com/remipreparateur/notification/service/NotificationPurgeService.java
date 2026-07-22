package com.remipreparateur.notification.service;

import com.remipreparateur.notification.repository.NotificationRepository;
import com.remipreparateur.plateforme.service.ParametrePlateformeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Purge de rétention des notifications : supprime les lignes trop anciennes selon les délais
 * réglés en plateforme (lues / non lues). Tourne chaque nuit et est aussi déclenchable
 * manuellement depuis la console super-admin. Heure réelle (jamais l'Horloge simulée).
 */
@Service
public class NotificationPurgeService {

    private static final Logger log = LoggerFactory.getLogger(NotificationPurgeService.class);

    private static final int DEFAUT_LUES = 30;
    private static final int DEFAUT_NON_LUES = 90;

    private final NotificationRepository notificationRepository;
    private final ParametrePlateformeService parametres;

    public NotificationPurgeService(NotificationRepository notificationRepository,
                                    ParametrePlateformeService parametres) {
        this.notificationRepository = notificationRepository;
        this.parametres = parametres;
    }

    /** Purge selon les délais de rétention plateforme. Renvoie le nombre de notifications supprimées. */
    @Transactional
    public int purger() {
        int joursLues = Math.max(1, parametres.getInt(ParametrePlateformeService.CLE_RETENTION_LUES, DEFAUT_LUES));
        int joursNonLues = Math.max(1, parametres.getInt(ParametrePlateformeService.CLE_RETENTION_NON_LUES, DEFAUT_NON_LUES));
        LocalDateTime maintenant = LocalDateTime.now();
        return notificationRepository.purgerAnciennes(
                maintenant.minusDays(joursLues), maintenant.minusDays(joursNonLues));
    }

    /** Purge quotidienne à 04:00 (heure réelle). */
    @Scheduled(cron = "0 0 4 * * *")
    public void purgeQuotidienne() {
        try {
            int n = purger();
            if (n > 0) log.info("Purge notifications : {} supprimée(s).", n);
        } catch (Exception e) {
            log.warn("Purge notifications : {}", e.getMessage());
        }
    }
}
